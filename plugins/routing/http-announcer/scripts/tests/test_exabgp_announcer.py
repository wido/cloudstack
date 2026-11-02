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
""" Run with: python3 -m unittest discover -s plugins/routing/http-announcer/scripts/tests """
import importlib.util
import json
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, '..', 'exabgp-announcer.py')


def load():
    spec = importlib.util.spec_from_file_location('exabgp_announcer', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TestAnnouncer(unittest.TestCase):

    def setUp(self):
        self.mod = load()
        self.commands = []
        self.state = tempfile.NamedTemporaryFile(suffix='.json', delete=False).name
        os.unlink(self.state)
        self.announcer = self.mod.Announcer(self.commands.append, self.state)

    def tearDown(self):
        if os.path.exists(self.state):
            os.unlink(self.state)

    def test_announce_withdraw_and_state(self):
        self.announcer.announce('203.0.113.0/29', '192.0.2.10')
        self.announcer.announce('2001:db8:1::/64', '2001:db8::10')
        self.assertEqual(['announce route 203.0.113.0/29 next-hop 192.0.2.10',
                          'announce route 2001:db8:1::/64 next-hop 2001:db8::10'], self.commands)
        with open(self.state) as f:
            self.assertEqual(2, len(json.load(f)['routes']))
        self.assertTrue(self.announcer.withdraw('203.0.113.0/29'))
        self.assertFalse(self.announcer.withdraw('203.0.113.0/29'))
        self.assertEqual('withdraw route 203.0.113.0/29 next-hop 192.0.2.10', self.commands[-1])

    def test_new_next_hop_withdraws_the_old_one_first(self):
        self.announcer.announce('203.0.113.0/29', '192.0.2.10')
        self.announcer.announce('203.0.113.0/29', '192.0.2.11')
        self.assertEqual(['announce route 203.0.113.0/29 next-hop 192.0.2.10',
                          'withdraw route 203.0.113.0/29 next-hop 192.0.2.10',
                          'announce route 203.0.113.0/29 next-hop 192.0.2.11'], self.commands)

    def test_withdraw_with_other_next_hop_is_refused(self):
        self.announcer.announce('203.0.113.0/29', '192.0.2.10')
        self.assertFalse(self.announcer.withdraw('203.0.113.0/29', '192.0.2.99'))
        self.assertEqual(1, len(self.announcer.routes))

    def test_replay_after_restart(self):
        self.announcer.announce('203.0.113.0/29', '192.0.2.10')
        restarted = self.mod.Announcer(self.commands.append, self.state)
        self.assertEqual(1, restarted.load())
        self.commands.clear()
        restarted.replay()
        self.assertEqual(['announce route 203.0.113.0/29 next-hop 192.0.2.10'], self.commands)


class TestHttpContract(unittest.TestCase):

    def setUp(self):
        self.mod = load()
        self.commands = []
        self.announcer = self.mod.Announcer(self.commands.append, None)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), self.mod.make_handler(self.announcer, 'secret'))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = 'http://127.0.0.1:%d' % self.server.server_address[1]

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def call(self, method, path, body=None, token='secret'):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data=data, method=method)
        if token:
            request.add_header('Authorization', 'Bearer ' + token)
        if data is not None:
            request.add_header('Content-Type', 'application/json')
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, json.loads(response.read() or b'null')
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read() or b'null')

    def test_full_cycle(self):
        status, health = self.call('GET', '/v1/health')
        self.assertEqual(200, status)
        self.assertEqual(self.announcer.instance, health['instance'])

        status, _ = self.call('PUT', '/v1/routes/203.0.113.0%2F29', {'next_hop': '192.0.2.10'})
        self.assertEqual(200, status)
        status, routes = self.call('GET', '/v1/routes')
        self.assertEqual([{'prefix': '203.0.113.0/29', 'next_hop': '192.0.2.10'}], routes)

        status, _ = self.call('DELETE', '/v1/routes/203.0.113.0%2F29?next_hop=192.0.2.10')
        self.assertEqual(200, status)
        status, _ = self.call('DELETE', '/v1/routes/203.0.113.0%2F29')
        self.assertEqual(404, status)
        self.assertEqual(['announce route 203.0.113.0/29 next-hop 192.0.2.10',
                          'withdraw route 203.0.113.0/29 next-hop 192.0.2.10'], self.commands)

    def test_rejects_bad_token_and_bad_input(self):
        self.assertEqual(401, self.call('GET', '/v1/routes', token='wrong')[0])
        self.assertEqual(401, self.call('GET', '/v1/routes', token=None)[0])
        self.assertEqual(400, self.call('PUT', '/v1/routes/not-a-prefix', {'next_hop': '192.0.2.10'})[0])
        self.assertEqual(400, self.call('PUT', '/v1/routes/203.0.113.0%2F29', {'next_hop': '2001:db8::1'})[0])
        self.assertEqual(400, self.call('PUT', '/v1/routes/203.0.113.0%2F29', {'next_hop': 'nope'})[0])
        self.assertEqual([], self.commands)


if __name__ == '__main__':
    unittest.main()
