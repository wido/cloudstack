# HTTP Route Announcer

Management-server plugin that gets Static-mode routed subnets (see
`docs/design/routed-subnets.md`) into the routing fabric. It knows nothing about BGP: it talks to
one or more *announcer daemons* over a four-call HTTP contract, and the daemon drives the speaker.
`scripts/exabgp-announcer.py` is the reference daemon, for ExaBGP; anything that answers the same
four calls works, so a GoBGP, BIRD or vendor-API shim is a small script away.

## Settings (per zone)

| setting | meaning |
|---|---|
| `routed.subnet.announcer` | `http` selects this plugin for the zone |
| `routed.subnet.announcer.http.endpoints` | comma separated base URLs of the daemons, e.g. `http://10.0.0.5:8900,http://10.0.0.6:8900` |
| `routed.subnet.announcer.http.token` | bearer token the daemons expect |
| `routed.subnet.announcer.http.timeout` | seconds before a daemon counts as unreachable (10) |
| `routed.subnet.announcer.strategy` | `all`: every announcement to every endpoint; `failover`: the first that accepts |

The management server records, per subnet and endpoint, what each endpoint confirmed. A failed
announcement leaves the subnet assigned and shown as *Pending* or *Degraded*; the reconciler
(`routed.subnet.reconcile.interval`) and the health check
(`routed.subnet.announcer.health.interval`) retry, and an endpoint that comes back or restarted is
resynchronised at once. `restartNetwork` on the subnet's network and the `reannounceRoutedSubnet`
API push the routes again by hand.

## The contract

```
GET    /v1/routes                        -> [{"prefix": "...", "next_hop": "..."}, ...]
PUT    /v1/routes/<prefix>               body {"next_hop": "..."}   idempotent
DELETE /v1/routes/<prefix>?next_hop=...                            idempotent, 404 is fine
GET    /v1/health                        -> {"status": "ok", "instance": "<id>"}
Authorization: Bearer <token>
```

`<prefix>` is URL-encoded CIDR. The plugin sends prefix and next hop and nothing else; local ASN,
peers, communities and filters are the speaker's own configuration. `instance` is an id the daemon
picks when it starts: a change tells the management server the daemon restarted and its routes
must be replayed.

## The reference daemon

`exabgp-announcer.py` runs as an ExaBGP `process`, writes `announce route ... next-hop ...` and
`withdraw route ...` to ExaBGP, keeps every accepted route in a JSON state file and replays the
file when ExaBGP starts it. Python 3 standard library only.

```
process cloudstack {
    run /usr/bin/python3 /opt/cloudstack/exabgp-announcer.py --listen 10.0.0.5:8900
        --token-file /etc/exabgp/announcer.token --state /var/lib/exabgp/announcer.json;
    encoder json;
    on-exit keep;
}

neighbor 10.0.0.1 {
    router-id 10.0.0.5;
    local-address 10.0.0.5;
    local-as 65001;
    peer-as 65000;
    family {
        ipv4 unicast;
        ipv6 unicast;
    }
}
```

`on-exit keep` matters on ExaBGP 6, which otherwise withdraws a process's routes when the process
exits. ExaBGP 5 is the current LTS; Ze, ExaBGP's successor, runs ExaBGP process scripts through
`ze exabgp plugin`, so the daemon carries over.

Two daemons behind two speakers, each peering with the fabric, and strategy `all` is the
recommended deployment: one speaker going down then takes nothing with it. Bind the daemon to a
management network; the token is the only protection.

Tests: `python3 -m unittest discover -s plugins/routing/http-announcer/scripts/tests`.
