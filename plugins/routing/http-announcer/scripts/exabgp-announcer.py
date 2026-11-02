#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
"""
Reference route announcer daemon for CloudStack's HTTP Route Announcer plugin, driving ExaBGP.

ExaBGP starts this script as a "process" and reads route commands from its stdout. The script
serves the plugin's HTTP contract, keeps every accepted route in a state file, and replays the
file into ExaBGP whenever it starts, so an ExaBGP restart brings the routes back without the
management server. The management server reconciles against GET /v1/routes anyway, so a lost
state file only delays recovery until the next health check or reconcile run.

    GET    /v1/routes                       -> [{"prefix": "...", "next_hop": "..."}, ...]
    PUT    /v1/routes/<prefix>              body {"next_hop": "..."}; idempotent
    DELETE /v1/routes/<prefix>?next_hop=... idempotent, 404 when not held
    GET    /v1/health                       -> {"status": "ok", "instance": "<id>", "routes": n}

<prefix> is URL-encoded. Every request carries "Authorization: Bearer <token>".

Run it from ExaBGP, for example:

    process cloudstack {
        run /usr/bin/python3 /opt/cloudstack/exabgp-announcer.py --listen 10.0.0.5:8900
            --token-file /etc/exabgp/announcer.token --state /var/lib/exabgp/announcer.json;
        encoder json;
        on-exit keep;
    }

ExaBGP's own configuration (local ASN, neighbours, next-hop handling, route-maps) is yours; the
daemon never touches it. "on-exit keep" matters on ExaBGP 6, which otherwise withdraws a
process's routes when the process exits. Standard library only.
"""
import argparse
import json
import logging
import os
import sys
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

LOG = logging.getLogger("exabgp-announcer")


def is_valid_prefix(prefix):
    """ A plain IPv4 or IPv6 CIDR; nothing else reaches ExaBGP. """
    if not prefix or '/' not in prefix:
        return False
    address, _, length = prefix.partition('/')
    if not length.isdigit():
        return False
    import ipaddress
    try:
        ipaddress.ip_network(prefix, strict=False)
    except ValueError:
        return False
    return 0 <= int(length) <= (128 if ':' in address else 32)


def is_valid_address(address):
    import ipaddress
    try:
        ipaddress.ip_address(address)
    except (ValueError, TypeError):
        return False
    return True


class Announcer:
    """ The route set and its two sinks: ExaBGP's stdin (through `write`) and the state file. """

    def __init__(self, write, state_path=None):
        self.write = write
        self.state_path = state_path
        self.routes = {}  # prefix -> next_hop
        self.lock = threading.Lock()
        self.instance = uuid.uuid4().hex

    def load(self):
        if not self.state_path or not os.path.exists(self.state_path):
            return 0
        try:
            with open(self.state_path) as f:
                data = json.load(f)
        except (OSError, ValueError) as e:
            LOG.warning("state file %s unreadable, starting empty: %s", self.state_path, e)
            return 0
        for route in data.get("routes", []):
            prefix, next_hop = route.get("prefix"), route.get("next_hop")
            if is_valid_prefix(prefix) and is_valid_address(next_hop):
                self.routes[prefix] = next_hop
        return len(self.routes)

    def save(self):
        if not self.state_path:
            return
        tmp = self.state_path + ".tmp"
        with open(tmp, "w") as f:
            json.dump({"routes": [{"prefix": p, "next_hop": n} for p, n in sorted(self.routes.items())]}, f)
        os.replace(tmp, self.state_path)

    def replay(self):
        """ Everything the state file held goes to ExaBGP again, for its empty RIB after a restart. """
        for prefix, next_hop in sorted(self.routes.items()):
            self.write("announce route %s next-hop %s" % (prefix, next_hop))
        LOG.info("replayed %d routes", len(self.routes))

    def announce(self, prefix, next_hop):
        with self.lock:
            previous = self.routes.get(prefix)
            if previous is not None and previous != next_hop:
                # one prefix, one next hop: the old announcement goes first
                self.write("withdraw route %s next-hop %s" % (prefix, previous))
            self.write("announce route %s next-hop %s" % (prefix, next_hop))
            self.routes[prefix] = next_hop
            self.save()

    def withdraw(self, prefix, next_hop=None):
        with self.lock:
            held = self.routes.get(prefix)
            if held is None:
                return False
            if next_hop is not None and next_hop != held:
                return False
            self.write("withdraw route %s next-hop %s" % (prefix, held))
            del self.routes[prefix]
            self.save()
            return True

    def listing(self):
        with self.lock:
            return [{"prefix": p, "next_hop": n} for p, n in sorted(self.routes.items())]


def make_handler(announcer, token):

    class Handler(BaseHTTPRequestHandler):
        server_version = "exabgp-announcer/1"

        def log_message(self, fmt, *args):
            LOG.debug("%s " + fmt, self.client_address[0], *args)

        def _send(self, status, payload=None):
            body = b"" if payload is None else json.dumps(payload).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if body:
                self.wfile.write(body)

        def _authorized(self):
            if not token:
                return True
            header = self.headers.get("Authorization", "")
            return header == "Bearer " + token

        def _route_prefix(self, path):
            if not path.startswith("/v1/routes/"):
                return None
            return unquote(path[len("/v1/routes/"):])

        def do_GET(self):
            if not self._authorized():
                return self._send(401, {"error": "unauthorized"})
            path = urlparse(self.path).path
            if path == "/v1/health":
                return self._send(200, {"status": "ok", "instance": announcer.instance, "routes": len(announcer.routes)})
            if path == "/v1/routes":
                return self._send(200, announcer.listing())
            self._send(404, {"error": "not found"})

        def do_PUT(self):
            if not self._authorized():
                return self._send(401, {"error": "unauthorized"})
            prefix = self._route_prefix(urlparse(self.path).path)
            if prefix is None:
                return self._send(404, {"error": "not found"})
            length = int(self.headers.get("Content-Length", "0") or 0)
            try:
                body = json.loads(self.rfile.read(length) or b"{}")
            except ValueError:
                return self._send(400, {"error": "body is not JSON"})
            next_hop = body.get("next_hop")
            if not is_valid_prefix(prefix):
                return self._send(400, {"error": "invalid prefix"})
            if not is_valid_address(next_hop):
                return self._send(400, {"error": "invalid next_hop"})
            if (':' in prefix) != (':' in next_hop):
                return self._send(400, {"error": "prefix and next_hop must be the same address family"})
            announcer.announce(prefix, next_hop)
            self._send(200, {"prefix": prefix, "next_hop": next_hop})

        def do_DELETE(self):
            if not self._authorized():
                return self._send(401, {"error": "unauthorized"})
            parsed = urlparse(self.path)
            prefix = self._route_prefix(parsed.path)
            if prefix is None:
                return self._send(404, {"error": "not found"})
            next_hop = parse_qs(parsed.query).get("next_hop", [None])[0]
            if announcer.withdraw(prefix, next_hop):
                return self._send(200, {"prefix": prefix})
            self._send(404, {"error": "not held"})

    return Handler


def exabgp_writer(stream):
    def write(command):
        stream.write(command + "\n")
        stream.flush()
    return write


def drain(stream):
    """ ExaBGP sends its own messages (JSON encoder) to our stdin; read them so it never blocks. """
    for _ in stream:
        pass


def main(argv=None):
    parser = argparse.ArgumentParser(description="HTTP route announcer for ExaBGP")
    parser.add_argument("--listen", default="127.0.0.1:8900", help="address:port to serve the API on")
    parser.add_argument("--token", default=os.environ.get("ANNOUNCER_TOKEN", ""), help="bearer token; empty disables authentication")
    parser.add_argument("--token-file", help="file holding the bearer token")
    parser.add_argument("--state", help="JSON state file, replayed into ExaBGP at start")
    parser.add_argument("--log-level", default="INFO")
    args = parser.parse_args(argv)
    logging.basicConfig(stream=sys.stderr, level=args.log_level, format="%(asctime)s %(name)s %(levelname)s %(message)s")

    token = args.token
    if args.token_file:
        with open(args.token_file) as f:
            token = f.read().strip()
    host, _, port = args.listen.rpartition(":")
    host = host.strip("[]")

    announcer = Announcer(exabgp_writer(sys.stdout), args.state)
    loaded = announcer.load()
    LOG.info("instance %s, %d routes from state, listening on %s", announcer.instance, loaded, args.listen)
    announcer.replay()

    threading.Thread(target=drain, args=(sys.stdin,), daemon=True).start()
    server = ThreadingHTTPServer((host, int(port)), make_handler(announcer, token))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
