# Routed Subnets for Instances

**Status:** implemented on the branch (commits listed in §15); §16 lists what remains to verify
**Branch:** `routed-subnets` (on top of `direct-routed-network`, PR #13758)
**Author:** Wido den Hollander
**Last updated:** 2026-11-02

---

## 1. Summary

An Instance gets, in addition to the unicast address on its NIC, one or more **subnets** that the
network routes **to that unicast address**. An IPv4 subnet is /30 or larger, an IPv6 subnet is /64
or larger (a /64 is what every guest tool, Docker included, expects to be given). The Instance does not
run a routing protocol for this: CloudStack allocates the subnet from a zone pool and gets the
route into the network.

Two modes, chosen by the offering:

* **Static** — the management server announces `subnet via <Instance address>` into the routing
  fabric through a pluggable *route announcer*. The announcer shipped with this feature speaks a
  small generic HTTP API; the reference daemon behind that API drives ExaBGP. Nothing on the
  hypervisor or in the Instance speaks BGP.
* **BGP** — the Instance announces the subnet itself, over a (multihop) BGP session with a router
  the operator provides. CloudStack only allocates the subnet, records it, and opens the host-side
  anti-spoof and forwarding rules for it. In this mode the same subnet may be attached to several
  Instances of one account, which gives anycast.

The feature is for NICs on **Direct Routed (L3) and Shared** networks: anywhere the NIC's unicast
address is reachable from the fabric without NAT. On a Direct Routed host the agent also installs
`subnet via <Instance address>` in the host kernel, because the fabric delivers the subnet's packets
to the host that holds the Instance's /32 or /128, and the host must forward them on to the
Instance.

The subnet lifecycle is **assignment to release**, not Instance start to stop. The next hop is the
Instance's address, which the Direct Routed design already keeps host-independent, so the fabric
route never changes on start, stop or live migration.

## 2. Motivation

* Containers inside an Instance need addresses of their own. A /64 lets Docker or a container
  network plugin hand out IPv6 addresses to containers without any renumbering tricks; a /30 or
  /29 lets an Instance run a few services on distinct IPv4 addresses without burning pool
  addresses one by one as secondary IPs.
* Secondary IPs do not scale to that: each one is a pool address, an ipset entry, a host route and,
  on Shared networks, an ARP entry in the fabric. A routed subnet is one route.
* Operators running routed fabrics (the audience of Direct Routed networks) already expect to route
  prefixes to hosts. Routing a prefix to an Instance is the same operation one level down.
* Anycast services (DNS resolvers, ingress) need the same prefix on several Instances and are the
  main reason the BGP mode exists.

## 3. Non-goals

* No routing daemon on the hypervisor is configured by CloudStack (unchanged from Direct Routed
  §10).
* No BGP session between CloudStack and the Instance, and no BGP session between the hypervisor and
  the Instance that CloudStack sets up. In BGP mode the peer is the operator's router, reached
  multihop through the Instance's unicast address.
* No VRFs, no overlapping subnets between accounts. Pools are routable, unique address space.
* No support on Isolated or VPC networks, including Routed-mode Isolated networks (the VR would
  need the route; later, if wanted).
* No ECMP in Static mode in v1 (the announcer gets exactly one next hop per subnet).
* Non-KVM hypervisors (same as Direct Routed).

## 4. Terminology

* **Routed Subnet** — an IPv4 or IPv6 prefix owned by an account and routed to one (Static) or
  several (BGP) NICs.
* **Next hop NIC** — the NIC whose unicast address the subnet is routed to. IPv4 subnets use the
  NIC's IPv4 address, IPv6 subnets the NIC's IPv6 address.
* **Pool** — a zone-level prefix subnets are carved from. Optionally dedicated to a domain or
  account.
* **Routed Subnet Offering** — what a user picks: it fixes the families, the prefix lengths and the
  mode. Users never choose a size.
* **Route announcer** — a management-server plugin that gets `prefix via next-hop` into the fabric.
* **Announcement** — one `(prefix, next hop)` pair the announcer is asked to hold.

## 5. Model

### 5.1 What the Instance sees

The subnet is routed to the NIC's address, nothing more. The guest decides what to do with it:
put it on a dummy or loopback interface, hand it to Docker, or run a service bound to addresses in
it. CloudStack tells the guest about it through ConfigDrive (§8); it does not configure it.

### 5.2 Packet walk — Static mode, Direct Routed network

1. A packet for an address in the subnet enters the fabric. The fabric has `subnet via <nic ip>`
   from the announcer; `<nic ip>` resolves recursively to the /32 or /128 the Instance's host
   advertises. The packet goes to the host.
2. The host kernel has `subnet via <nic ip> dev brdr-<id>` (installed by the agent on NIC plug).
   `<nic ip>` has a permanent neighbour entry on the bridge. The packet is forwarded to the
   Instance's VIF.
3. The Instance replies from an address in the subnet. The bridge's `rp_filter=1` finds the route
   via the bridge for the source, so the reply passes. The reply leaves via the host's default
   route as any other guest traffic.

### 5.3 Packet walk — Static mode, Shared network

1. The fabric has `subnet via <nic ip>`; `<nic ip>` is in a connected VLAN of the operator's
   router. The router ARPs or NDs for the Instance, as it already does for the unicast address.
2. The hypervisor is a bridge; nothing routes. Only the security-group ipset needs to know the
   subnet so the anti-spoof rule lets replies from it out.

### 5.4 Packet walk — BGP mode

Identical in the data plane. The difference is only who announced: the Instance did, over a BGP
session to a router the operator runs, with the session's packets travelling as ordinary unicast
from the NIC's address. The host-side route (Direct Routed) and ipset entries are still installed
by CloudStack, from the subnet it allocated — the host must forward the subnet to the Instance
whatever the fabric learned it from.

### 5.5 Lifecycle

| Event | Static | BGP |
|---|---|---|
| assign | carve or reuse subnet(s), map to NIC, announce via NIC address, host route + ipset if Instance is running | carve or reuse, map to NIC, host route + ipset if running |
| announce fails | subnet stays Allocated, `announced=false` (shown as Pending); reconciler and network restart retry | n/a |
| Instance stop | nothing in the fabric (Direct Routed: host withdraws the /32, the subnet route's next hop becomes unresolvable and is held down by the fabric) | nothing (the Instance's session drops) |
| Instance start / migrate | host route and ipset go with NIC plug, as for the primary address | same |
| release | withdraw, unmap, subnet returns to the pool as Free | unmap; subnet returns to the pool when the last NIC releases it |
| withdraw fails | row goes to Releasing, reconciler retries, user is not blocked | n/a |
| NIC removed / Instance expunged | release of every subnet on the NIC | same |
| network restart | re-announce every Static subnet on the network's NICs | nothing |
| account removed | release all | release all |

## 6. Design decisions

### 6.1 Next hop is always the NIC's unicast address **DECIDED**

Never the host. The host-independence of the Instance address is the whole point of the Direct
Routed design (§6.3.3, §11 there): the announcer acts on assign and release only, and no fabric
state has to follow start, stop or migration. On Shared networks the host is not a router anyway.

Consequence: the NIC must hold an address of the subnet's family. An offering with an IPv6 part
cannot be assigned to a NIC without an IPv6 address. Validated at assign.

### 6.2 Offerings fix families, sizes and mode **DECIDED**

A new entity, *Routed Subnet Offering*:

| column | meaning |
|---|---|
| name, display_text, uuid, state | as other offerings |
| ip4_prefix_length (nullable) | e.g. 29; NULL means the offering hands out no IPv4 subnet |
| ip6_prefix_length (nullable) | e.g. 80; NULL means no IPv6 subnet |
| mode | `Static` or `BGP` |
| domain_id (nullable), zone_id (nullable) | visibility, as network offerings |
| details | key/value, used by BGP mode for peer information handed to the guest (§6.8, §8) |

At least one prefix length is set. One assignment with a dual offering hands out both subnets
**atomically**: if the second family cannot be carved (pool exhausted, limit hit) the first is
rolled back and the call fails. A NIC lacking an address of one of the offering's families is
rejected at assign; the user picks a single-family offering instead.

Users cannot pass a size or a specific subnet. Root admins can, to repair or import (optional
`ip4subnet=` / `ip6subnet=` on the assign call, admin only).

Validation at offering creation and at assign: each prefix length between the hard floor (30 / 64)
and the zone's shortest allowed prefix (§6.5).

### 6.3 Pools: the Routed-mode and IPv6-prefix tables, extended **DECIDED**

The four existing tables are reused with added columns; no new pool tables.

**`dc_ip4_guest_subnets`** (zone pool: `data_center_id, subnet, domain_id, account_id`) gains

* `purpose` varchar, `Network` (default, today's Routed-mode use) or `Instance`.

**`ip4_guest_subnet_network_map`** (child: `parent_id, subnet, network_id, vpc_id, state,
allocated`) gains

* `nic_id` bigint nullable — the next-hop NIC. Exactly one of `network_id`, `vpc_id`, `nic_id` is
  set on an Allocated row.
* `offering_id` bigint nullable — the Routed Subnet Offering; set means purpose Instance.
* `account_id`, `domain_id` nullable — the owner, so listing, counting and account cleanup need
  no join through the NIC (added during implementation; NULL on Routed-mode network rows, whose
  owner is their network).
* state values: existing `Free`/`Allocated` plus `Releasing` (withdraw pending). What each
  announcer endpoint holds lives in its own table (§6.7.1), not on the row.

**`dc_ip6_guest_prefix`** (zone pool: `data_center_id, prefix`) gains

* `domain_id`, `account_id` nullable — dedication, as the IPv4 pool has. Today the IPv6 pool has
  none; the new columns are NULL for existing rows and the existing allocator ignores them, so
  behaviour for Isolated networks is unchanged.
* `purpose` as above.

**`ip6_guest_prefix_subnet_network_map`** (child: `prefix_id, subnet, network_id, state`) gains
`nic_id`, `offering_id`, `account_id`, `domain_id` and the `Releasing` state, as the IPv4 child.

**Why `purpose` is needed — DECIDED: a flag.** The IPv6 allocator for Isolated networks
(`Ipv6ServiceImpl.preallocatePrefixSubnetRandomly`) splits a prefix into /64s and picks a free one
at random, checking only for a child row with exactly that /64 string. It does not check
containment, so it would happily pick a /64 inside a /56 carved for an Instance. Rather than
teach both allocators about each other, a pool serves one purpose. The IPv4 side could share
pools safely (both carvers see every child through the gap computation), but the same flag keeps
an operator's public space apart from the Routed-mode Isolated space, which is usually the intent
anyway.

**Allocation.** The Routed-mode carver (`RoutedIpv4ManagerImpl.createIpv4SubnetStringFromParentSubnet`,
IPv4 only, best fit in `long`) stays as it is for guest networks; the Instance path uses
`SubnetCarver`, one first-fit scan in `BigInteger` for both families, with its own unit tests.
Two carvers over the same child rows are safe because every child is visible to both through
the gap computation. IPv6 uses `BigInteger`, which
`NetUtils` already uses for the EUI-64 and range code. Pool lookup order (account-dedicated,
domain-dedicated, undedicated) and the "reuse a Free child of this exact size first" rule
(`findFirstAvailable(parentId, cidrSize)`) are kept. Ownership of an Allocated row is derived from
the NIC's Instance, as today it is derived from the network; Free rows have no owner.

**Anycast rows.** In BGP mode a second attachment of a subnet creates another child row with the
same `subnet` and `parent_id` and its own `nic_id`. The gap computation is unaffected by duplicate
ranges. On release, a row whose subnet still has other Allocated rows is removed outright; the last
one goes back to Free.

### 6.4 Dedication and limits **DECIDED**

* A pool is zone-wide, or dedicated to a domain or an account. At assign the candidate pools are
  tried in the order account-dedicated, domain-dedicated, undedicated, as Routed mode does.
* A new resource type `routed_subnet` in `Resource.ResourceType` (after `gpu`, ordinal 17),
  counted per account and domain like `public_ip`. The count is per subnet, independent of family
  and size; sizes are controlled by offerings, not by quota. A dual-family assignment counts two.
  Default limit through the usual `max.account.*` / `max.domain.*` / `max.project.*` settings.
  The anycast case counts the subnet once, for the owning account.
* No usage records in v1; the limit is the control.

### 6.5 Size bounds **DECIDED**

Hard floors: IPv4 /30, IPv6 /64. Shortest prefix per zone, as zone-scoped settings:

* `routed.subnet.ipv4.shortest.prefix` (default 24)
* `routed.subnet.ipv6.shortest.prefix` (default 56)

Enforced at offering creation and again at assign (the setting may have been tightened since).

### 6.6 Supported networks **DECIDED**

* `GuestType.L3` (Direct Routed): full support, host route installed by the agent.
* `GuestType.Shared`: supported, no host involvement beyond the ipset. The operator's router must
  resolve the NIC address in the VLAN; nothing to do for CloudStack. With security groups
  disabled nothing on the host binds the subnet to the Instance at all; that is the accepted
  side-effect of running without security groups, the same as for the unicast address today.
* Isolated, VPC tiers, L2: rejected at assign.

### 6.7 The announcer is a standalone adapter, not a NetworkElement **DECIDED**

`NetworkElement.prepare()`/`release()` follow the Instance lifecycle and the offering's service
map. Neither fits: the route lives from assign to release regardless of Instance state, and it
must work on Shared networks whose offerings predate this feature. So:

```java
public interface RouteAnnouncer extends Adapter {
    List<String> getEndpoints(long zoneId);
    void announce(long zoneId, String endpoint, String prefix, String nextHop) throws RouteAnnouncementException;
    void withdraw(long zoneId, String endpoint, String prefix, String nextHop) throws RouteAnnouncementException;
    Set<Announcement> list(long zoneId, String endpoint) throws RouteAnnouncementException;
    String getInstanceId(long zoneId, String endpoint) throws RouteAnnouncementException;
}
```

The SPI is per endpoint, so the strategy (§6.7) and the per-endpoint records (§6.7.1) live in
the manager and every announcer gets them for free. Announcers register through the
`routeAnnouncersRegistry` extension registry (core module `routing`, plugins declare
`parent=routing`); a zone-scoped setting `routed.subnet.announcer` names the one to use by its
adapter name (empty: Static-mode offerings cannot be assigned in that zone). `list()` and
`getInstanceId()` exist for reconciliation (§10).

**Failure semantics — Pending, never fail the user.**

* `assign` allocates and maps first, then announces. If an endpoint rejects or is unreachable,
  the row keeps state `Allocated` and the API shows `announced` as `none`, `partial` or `all`
  (§6.7.1); the UI renders `none` as *Pending* and `partial` as *Degraded*. The user has the
  subnet and can prepare the guest; the route follows when the announcer is back.
* Re-announcement happens from three places: the periodic reconciler (§10), management-server
  start, and **`restartNetwork`** on the Shared or L3 network, which re-announces every Static
  subnet on that network's NICs. The restart is the operator's explicit "push it again".
* `release` withdraws then frees. If withdraw fails the row goes to `Releasing`; the reconciler
  retries and frees it when the withdraw succeeds. The subnet is not handed out again while
  `Releasing`, so a stale announcement can never point at a new owner.

**Endpoint strategy — the admin's choice.** The announcer plugin takes a list of endpoints and a
zone setting `routed.subnet.announcer.strategy`:

* `all` — every announcement goes to every endpoint; `announced=all` only when every endpoint
  accepted, `partial` otherwise, and the reconciler fills in what is missing. For a pair of
  ExaBGP speakers that both peer with the fabric.
* `failover` — endpoints tried in order, the first that accepts wins; `announced=all` means the
  active endpoint holds it. The reconciler replays the full set into an endpoint when it becomes
  the active one.

Default `all`.

#### 6.7.1 Per-endpoint record **DECIDED**

What each endpoint has confirmed is stored, not summarised into one flag:

```
routed_subnet_announcements
  id, address_family, subnet_map_id, endpoint, announced (datetime, NULL = not held),
  last_error varchar, last_attempt datetime, created, removed
```

One row per `(map row, endpoint)`, created when the endpoint is first tried. The API derives
`none`/`partial`/`all` (with the failover strategy, one held endpoint is `all`) and lists
the endpoints with their state and last error. The reconciler retries only the rows that are not
held, and an admin can see at a glance that one speaker has been behind for an hour. The rows are
removed with the map row once every endpoint has confirmed the withdraw.

### 6.8 BGP mode and anycast **DECIDED**

* CloudStack does not announce. It allocates and records the subnet, installs host route and ipset
  entries, and hands the guest what it needs to configure its BGP daemon (§8).
* The same subnet may be attached to several NICs **of the same account**. The API form is
  `assignRoutedSubnet nicid=<n> subnetid=<existing>` instead of `offeringid=`. Cross-account
  attachment is rejected. Static-mode subnets reject a second attachment.
* Two anycast NICs on one Direct Routed host: see §6.9.

**Where the peer information comes from — DECIDED: the 4.20 BGP tables, one ASN per
Instance.**

* **Peers** come from the existing `bgp_peers` table (`listBgpPeers`: zone, IPv4/IPv6 address,
  ASN, password, dedicated to a domain or account or system-wide). The lookup is the one
  `BGPServiceImpl.getBgpPeersForNetwork` already does: peers dedicated to the account first, then
  the domain, then the system peers. An offering can narrow this with a `bgp.peers` detail
  listing peer ids, otherwise all applicable peers are handed to the guest.
* **The local ASN is allocated per Instance** from the zone's AS-number ranges
  (`as_number_range` / `as_number`, `BGPService.allocateASNumber`) when the first BGP-mode subnet
  is assigned to any of its NICs, and released when the last one is released or the Instance is
  expunged. `as_number` gains a `vm_id` column next to `network_id` and `vpc_id`. Every Instance
  speaks with its own ASN; anycast members therefore peer as distinct ASNs announcing the same
  prefix, which is the normal anycast shape and lets the upstream router filter per Instance.
  Assigning a BGP-mode subnet in a zone without a free AS number fails with that message.
* **The upstream side is the operator's.** CloudStack hands the guest the peers and ASNs and
  records which Instance has which ASN; it does not configure the router. The admin of the
  upstream router configures each Instance as a neighbour (address = the NIC's unicast address,
  ASN from `listASNumbers`, multihop) and the prefix-list for it (from `listRoutedSubnets`).
  This must be stated plainly in the operator documentation: without that configuration the
  Instance's session never comes up and the subnet is unreachable, and CloudStack cannot tell.
  A listing that joins Instance, NIC address, ASN and subnets (`listRoutedSubnets` with
  `mode=BGP`) is the hand-off to whatever automates the router.
* Rejected alternatives, for the record: ASNs and peers in offering details (one ASN for every
  Instance of the offering, passwords in details), zone-wide settings (no per-tenant variation),
  per-assignment parameters (the peers are the operator's, not the user's).

### 6.9 Host side (KVM, Direct Routed only) **DECIDED**

The agent's job grows by one route per subnet, in the same place as the per-address work of Direct
Routed §9.1:

* `NicTO` gains `List<String> routedSubnets` (CIDRs, both families). `BridgeVifDriver` passes them
  to `modifymacip.sh` on plug; the script gains `-r <cidr>` (repeatable) and runs
  `ip route replace <cidr> via <nic address of that family> dev <bridge> metric <m>`. `-o delete`
  by MAC removes routes whose `via` is one of the MAC's addresses (`ip route show via <addr>`),
  before the addresses themselves. Per-address delete removes the subnets of that address too.
* Hot assign/release on a running Instance: a new `ModifyRoutedSubnetCommand` (nic, address,
  subnet, add/remove) sent to the host. Direct Routed hosts install or remove the route; every
  host updates the ipset when security groups are enabled. The command is sent only when the
  Instance is Running; otherwise the next plug does the work.
* Migration needs nothing new: the destination plugs with the full NicTO, the source unplugs by
  MAC.
* `rp_filter=1` on the bridge is compatible: the reverse path for a subnet source is the bridge.

**Two anycast Instances on the same host — the problem, and the fix.** A Linux route is keyed by
`(prefix, metric, table)`. Instance A plugs and the agent runs `ip route replace S via A dev br`.
Instance B, same subnet S, plugs on the same host and runs `ip route replace S via B dev br`: same
key, so the kernel **overwrites** the route. That alone is harmless for anycast, since either
Instance serves S. The damage comes at unplug: B migrates away, the agent runs
`ip route del S via B`, and the host now has **no** route for S while A is still running on it.
The fabric keeps sending S to this host because A's /32 is here; the host forwards S out of its
default route, back into the fabric: loop or black hole until A is restarted. Shared networks are
unaffected (no host routes).

Three ways out:

1. **Per-next-hop metric** (DECIDED). The route is installed with `metric <m>` where `m` is derived
   from the next-hop address: the IPv4 address as a 32-bit integer; for IPv6 the low 32 bits of
   the address (unique within a /64 for EUI-64 addresses, and a collision only means an
   overwrite, never a loss). Two routes to S with different metrics coexist; the kernel uses the
   lowest; `ip route del S via B metric m_B` removes exactly B's. No reference counting, no
   knowledge of other Instances needed on the host, and it costs one argument.
2. **Multipath route** (`S nexthop via A nexthop via B`). Real ECMP on the host, but the agent must
   know every local next hop on each add and delete, so the command has to carry the full set and
   the management server must track which anycast members share a host. More moving parts for a
   case that only occurs when a placement happens to co-locate members.
3. **Anti-affinity.** Auto-create a host anti-affinity group per anycast subnet. Placement is
   advisory under pressure and migration can still co-locate; and it couples a routing detail to
   the deployment planner. Rejected.

Metric is applied to **all** routed-subnet routes, anycast or not, so the script has one code
path.

### 6.10 Security groups **DECIDED**

* The per-Instance IPv4 ipset becomes `hash:net` (the IPv6 one already is). Created fresh per
  Instance start, so no in-place conversion is needed; a running Instance keeps its `iphash` set
  until restart, and the hot-assign command recreates the set if its type is wrong.
* `security_group.py` gains `--subnets <cidr,cidr>`; each is added to the family's ipset. The
  from-Instance anti-spoof rule (`! --match-set <set> src -j DROP`) and the towards-Instance
  dispatch (`--match-set <set> dst`) then cover the subnet with no rule changes, on both the
  classic and the L3 path.
* Ingress and egress rules apply to subnet addresses exactly as to the primary address (the rules
  are per Instance, not per address). No new semantics.
* ebtables ARP rules are untouched: a routed subnet is never ARPed for.

### 6.11 Switching a subnet between Static and BGP **DECIDED — by changing its offering**

A subnet moves between the two modes the way an Instance changes its compute offering or a
network its network offering: `updateRoutedSubnet id= offeringid=` with an offering of the other
mode. The mode is a property of the offering, so the offering swap *is* the mode switch; there is
no separate `mode` parameter. Constraints:

* The new offering must have the same families and prefix lengths as the current one; nothing is
  re-carved. Offerings themselves are immutable in mode and sizes once a subnet uses them
  (`updateRoutedSubnetOffering` changes name, display text, state and visibility only).
* **Static → BGP.** The Instance gets an ASN if it has none yet (§6.8), the metadata gains the
  `routed-subnets-bgp` file at the next start (§8), then every endpoint's announcement is withdrawn (per-endpoint
  rows, §6.7.1, so a slow endpoint cannot block the switch). The documented order for the user is:
  bring the BGP session up on the Instance **before** switching, while CloudStack's announcement
  is still in place. Two sources announcing the same prefix is harmless; a gap is not. CloudStack
  cannot see the Instance's session and does not try to.
* **BGP → Static.** Only if the subnet is attached to exactly one NIC (Static has one next hop);
  anycast members must be released down to one first. CloudStack announces, rebuilds ConfigDrive
  without the BGP file, and releases the Instance's ASN when this was its last BGP-mode subnet.
  The user is expected to stop announcing from the Instance afterwards; until they do, the fabric
  sees the prefix twice, which again is harmless.
* The host side does not change: the route and the ipset entries are the same in both modes.
* Mode switches are events (`ROUTED.SUBNET.UPDATE`) and the response shows both the old and the
  new offering.

### 6.12 Not in v1

ECMP for Static mode (several next hops per announcement), announcer-side policy beyond a static
per-zone community list, IPv6 subnets on IPv4-only NICs via a secondary IPv6, usage records, UI
beyond listing and the admin forms.

## 7. API

Admin:

* `createIpv4SubnetForZone` / `createGuestNetworkIpv6Prefix` gain `purpose=Instance` (default
  `Network`); `createGuestNetworkIpv6Prefix` gains `domainid=` / `account=` for dedication; the
  existing dedicate/release-dedication calls for IPv4 subnets get IPv6 counterparts
  (`dedicateGuestNetworkIpv6Prefix`, `releaseDedicatedGuestNetworkIpv6Prefix`). List calls gain a
  `purpose` filter.
* `createRoutedSubnetOffering name= displaytext= [ip4prefixlength=] [ip6prefixlength=] mode=
  [domainid=] [zoneid=] [details]`, `updateRoutedSubnetOffering`, `deleteRoutedSubnetOffering`
* `updateResourceLimit resourcetype=17`
* `reannounceRoutedSubnet id=` — run the §10.1 sync for one subnet

User:

* `assignRoutedSubnet nicid= (offeringid= | subnetid=) [ip4subnet= ip6subnet= (admin)]` — async,
  returns the subnet(s)
* `releaseRoutedSubnet id= [nicid=]` — async; `nicid` is required when the subnet is on several
  NICs
* `updateRoutedSubnet id= offeringid=` — async; the mode switch (§6.11)
* `listRoutedSubnets [id] [nicid] [virtualmachineid] [account/domain/project] [zoneid]
  [announced=false]`
* `listRoutedSubnetOfferings`

Responses: `routedsubnet` {id, cidr, addressfamily, mode, offering, state, announced, nics[{nicid,
vmid, nexthop}]}; the NIC response and `listNics` gain `routedsubnets[]`.

Events: `ROUTED.SUBNET.ASSIGN`, `ROUTED.SUBNET.RELEASE`, `ROUTED.SUBNET.ANNOUNCE.FAILED`, offering
create/delete.

## 8. ConfigDrive

The guest learns its subnets from the `cloudstack/metadata` folder, next to the existing
`service-offering` and friends, as `routed-subnets`: one line per subnet,
`<cidr> <mode> <nic mac>`. OpenStack `network_data.json` has no key for a delegated prefix and
cloud-init configures nothing from unknown keys, so the data is not put there.

BGP-mode subnets additionally get `routed-subnets-bgp`: `local_asn=<Instance ASN>` once, then one
`peer=<address> <asn> [password]` line per applicable peer (§6.8), so a guest can template an FRR
or BIRD config from them. The password is the one stored on the peer; it reaches only this
Instance's own ConfigDrive.

**When the guest sees it (found in implementation).** CloudStack never rebuilds a running
Instance's ConfigDrive; even a user-data change waits for the next start. The two files are
therefore current as of the Instance's last start or migration, and a subnet assigned to a
running Instance shows up in its metadata at the next start. The API (`listRoutedSubnets`) is
the live view. The Virtual Router serves the same files on networks that have one, through the
usual VM data push.

## 9. Agent (KVM)

Summary of §6.9 and §6.10 by file:

* `api/.../NicTO.java`: `routedSubnets`.
* `BridgeVifDriver.executeMacIpScript()`: pass `-r` per subnet on plug.
* `scripts/vm/network/vnet/modifymacip.sh`: `-r`, route via the family's address with the
  address-derived metric, delete-by-via.
* `LibvirtComputingResource`: handler for `ModifyRoutedSubnetCommand`, reusing
  `configureDirectRoutedSecondaryIp`'s shape.
* `LibvirtSecurityGroupRulesCommandWrapper` and `security_group.py`: `--subnets`, `hash:net`.

The agent never sees the mode. Static and BGP are identical on the host.

## 10. The HTTP announcer and the ExaBGP reference daemon

Nobody runs ExaBGP here yet, and other operators will have other speakers, so the management-server
side is **not** ExaBGP-specific. `plugins/routing/http-announcer` implements `RouteAnnouncer`
against a small HTTP contract; the ExaBGP daemon is one implementation of that contract, shipped as
a reference.

**The contract** (documented in the plugin, versioned at `/v1`):

```
GET    /v1/routes                      -> [{"prefix": "...", "next_hop": "..."}, ...]
PUT    /v1/routes/<prefix>             body {"next_hop": "..."}   idempotent
DELETE /v1/routes/<prefix>?next_hop=…                             idempotent, 404 is success
GET    /v1/health
Authorization: Bearer <token>
```

`<prefix>` is URL-encoded CIDR. The daemon owns no policy: ASN, peers, communities and filters are
its own configuration. The management server never sends anything but prefix and next hop.

Zone settings: `routed.subnet.announcer.endpoints` (comma-separated URLs),
`routed.subnet.announcer.token`, `routed.subnet.announcer.strategy` (§6.7),
`routed.subnet.announcer.timeout`, `routed.subnet.announcer.health.interval` (default 30 s).

`GET /v1/health` returns `{"status": "ok", "instance": "<random id chosen at daemon start>",
"routes": <count>}`. The `instance` field is how the management server notices a restart (§10.1).

**The reference daemon**, `exabgp-announcer.py` in the plugin's `scripts/`, runs as an ExaBGP
`process` (`run /usr/bin/python3 /opt/cloudstack/exabgp-announcer.py; encoder json;`). It serves
the contract, translates to `announce route <prefix> next-hop <nh>` / `withdraw route ...` on
ExaBGP's stdin, keeps its route set in a JSON state file and replays it when ExaBGP starts it.
Python standard library only. Anyone can replace it with a GoBGP, BIRD or vendor-API shim that
answers the same four calls.

**ExaBGP versions and Ze (checked 2026-11-02).**

* ExaBGP 5.0 is the current LTS; 6.0 requires Python 3.12+ and changes one thing that matters
  here: routes announced by a 6.0-style process are **withdrawn when the process exits** unless
  the process is configured `on-exit keep;`. The daemon uses the 4.x/5.x command syntax (kept
  compatible in 6) and the reference configuration sets `on-exit keep;` explicitly, so a daemon
  restart never withdraws anything; the replay on start then only confirms what ExaBGP still
  holds.
* **Ze** (github.com/ze-software/ze) is ExaBGP's successor by the same author: a from-scratch Go
  rewrite, AGPL-3.0-only, **pre-alpha, no releases, APIs and configuration syntax explicitly
  unstable**. It documents `ze exabgp plugin`, a bridge that runs existing ExaBGP process scripts,
  and `ze exabgp migrate` for configurations, but no REST API for route injection of its own;
  operation is through an SSH CLI and a web editor. So the reference daemon is expected to run
  under Ze through that bridge, unchanged, and a native Ze backend for the HTTP contract can be
  added once Ze has a stable API. Nothing in CloudStack couples to either speaker: the contract
  is the boundary, which is exactly why it exists. The AGPL licence does not touch CloudStack
  (separate process, no code shared), but it does mean Ze cannot be bundled in Apache packaging,
  the same as ExaBGP today.

### 10.1 Surviving an ExaBGP restart or outage

Three layers, each independent of the others; any one of them alone brings the routes back.

1. **The daemon replays its state file.** `exabgp-announcer.py` writes every accepted
   announcement to a JSON state file before acknowledging it. ExaBGP starts the daemon as its
   `process`; when ExaBGP (re)starts, so does the daemon, and the first thing it does is write
   every route from the state file to ExaBGP's stdin. ExaBGP's RIB is empty after a restart and
   this repopulates it before the fabric sessions have finished coming up. If the daemon alone
   exits, ExaBGP respawns it (its `api.respawn` behaviour, on by default) and the same replay
   runs. This layer needs no management server at all and is the fast path: the fabric sees the
   routes again within seconds of ExaBGP being back.
2. **The management server notices and resyncs.** The announcer plugin polls `GET /v1/health` of
   every endpoint every `routed.subnet.announcer.health.interval`. Two transitions trigger a full
   sync of that endpoint: unreachable → reachable, and a change of the `instance` id, which means
   the daemon restarted (with or without its state file). A full sync is `GET /v1/routes`, then
   PUT for every Allocated Static-mode subnet the endpoint lacks and DELETE for every route it
   holds that the DB does not know. This is what makes a lost or stale state file, or a freshly
   installed speaker, harmless: the DB is the source of truth and the endpoint is made to match
   it. While an endpoint is unreachable, assigns and releases still succeed: their
   `routed_subnet_announcement` rows for that endpoint stay unheld (§6.7.1) and are played in by
   the sync.
3. **The periodic reconciler.** Independently of health transitions, a background task
   (`routed.subnet.reconcile.interval`, default 300 s) runs the same full sync for every endpoint
   of every zone, and finishes `Releasing` rows. It also runs once at management-server start, so
   a management server that was itself down during an ExaBGP outage catches up. `restartNetwork`
   runs it for one network's subnets (§6.7), and the UI's **Re-announce** action
   (`reannounceRoutedSubnet id=`, admin) for one subnet.

What the fabric sees in the meantime is the operator's design: a single ExaBGP speaker that is
down takes its routes with it, which is why two speakers with strategy `all` are the recommended
deployment, and BGP graceful restart on the speaker's sessions can bridge short restarts without
any withdrawal at all. Both are ExaBGP and router configuration, outside CloudStack.

Withdraws during an outage follow the same path: the map row sits in `Releasing`, the sync
deletes the route once the endpoint is back, and only then is the subnet freed. A subnet is never
handed to a new owner while any endpoint might still carry its old announcement.

## 11. UI

In from the start, not a follow-up. Four places, matching where the UI keeps the neighbouring
features today.

**Network → Routed Subnets** (new child of the `network` section in
`ui/src/config/section/network.js`, next to Guest Networks, VPC and Public IPs; visible when
`listRoutedSubnets` is in the user's API list):

* List: CIDR, family, mode, offering, Instance and NIC (next-hop address), announcement state
  (`none`/`partial`/`all`, rendered Pending / Degraded / Announced; blank for BGP mode), account,
  zone.
* Details: the above plus, for Static mode, one row per endpoint with held-since and last error;
  for BGP mode, the Instance's ASN and the peers it was given. Tabs: details, events.
* Actions: **Assign** (Instance → NIC → offering; or, for an existing BGP subnet of the account,
  "attach to another NIC" for anycast), **Release** (per NIC when attached to several),
  **Change offering** (the mode switch, §6.11; the picker offers only offerings of the other
  mode with the same families and sizes), **Re-announce** (Static, admin: runs the §10.1 sync for
  this subnet; cheaper than restarting the network).

**Compute → Instance → NICs tab** (`ui/src/views/network/NicsTab.vue`): each NIC lists its routed
subnets under its secondary IPs, with assign and release buttons that open the same forms. This is
where most users will find the feature, so the forms are shared components, not copies.

**Service Offerings → Routed Subnet Offerings** (new child of `offering.js` next to Network and
VPC offerings): list, details, create form (name, description, IPv4 prefix length, IPv6 prefix
length, at least one; mode; zone; domain visibility; optional peer restriction for BGP mode),
update (name, description, state, visibility only, §6.11), delete.

**Infrastructure → Zone**:

* The **IPv4 Subnets**, **AS Numbers** and **BGP Peers** tabs are today shown only when the zone
  has Routed mode enabled (`record.routedmodeenabled` in `ui/src/config/section/infra/zones.js`).
  They must also show when routed subnets are in use. The zone response gains
  `routedsubnetsenabled`, true when the zone has at least one Instance-purpose pool or an
  announcer configured, and the three tabs show on either flag.
* The IPv4 subnet and IPv6 prefix forms (`Ipv4GuestSubnetsTab.vue`, the IPv6 prefix part of
  `ui/src/views/infra/network/IpRangesTabGuest.vue`) gain the `purpose` selector; the IPv6
  prefix list gains dedicate and release-dedication actions, mirroring the IPv4 ones.
* The AS Numbers tab shows the Instance an ASN belongs to, next to the network and VPC columns.
* Zone settings for the announcer, bounds and intervals appear through the normal settings tab.

**Account / Domain / Project → Configure Limits**: a `Routed subnets` row for the new resource
type (the limits tab is generic on `resourcetypename`; it needs the label and the enum value).

Labels in `ui/public/locales/en.json`; other locales fall back to English as usual.

## 12. Orchestration touchpoints

* `NetworkOrchestrator.release()` / `removeNic()` and `UserVmManagerImpl.expunge`: release
  every subnet of the NIC (withdraw, unmap, free). Order: withdraw before the NIC's address is
  released back to the pool, so no window exists where the next hop belongs to someone else. If
  the withdraw fails the row is `Releasing` and the NIC address is still released; the reconciler
  withdraws later, and the address pool must not hand that address out while any `Releasing` row
  references it (checked in `allocateDirectIp` for L3/Shared).
* `VirtualMachineManagerImpl` NicTO construction (`toNicTO`): fill `routedSubnets`.
* `SecurityGroupManagerImpl`: subnets added to the rule-set payload for the Instance.
* `ConfigDriveBuilder`: new metadata files; rebuild triggers.
* `ResourceLimitManagerImpl`: counting and checking for the new type.
* `NetworkServiceImpl.restartNetwork`: re-announce pass for Shared and L3 networks (they have no
  VR, so this is the whole restart for them).
* Account cleanup (`AccountManagerImpl.cleanupAccount`): release subnets, delete dedications.
* Zone deletion: refuse while Instance-purpose pools exist.

## 13. Security

* The subnet is owned by one account; attachment to NICs of another account is rejected in both
  modes. Pools dedicated to an account cannot feed another account.
* Anti-spoof: without the ipset entry, an Instance on a security-group host cannot send from the
  subnet. With it, it can send only from the subnet CloudStack gave it. Direct Routed hosts
  without security groups rely on the bridge isolation of Direct Routed §12.1, unchanged.
* In BGP mode the Instance can announce whatever its peer accepts; prefix-list filtering on the
  operator's router is the control, and CloudStack can only tell the operator what to allow
  (`listRoutedSubnets`, `listASNumbers`). Same boundary as Direct Routed §10.
* The announcer endpoint is a route-injection point for the whole zone. Bearer token, and the
  operator should bind it to a management network. Documented, not enforced.
* BGP peer passwords are already stored in `bgp_peers` and shown only to admins; a guest sees
  only its own peers' passwords through its own ConfigDrive.

## 14. Upgrade and compatibility

* New columns on four existing tables (all nullable or defaulted), one new offering table, one
  announcement table, a new resource type. No existing row changes meaning: `purpose` defaults to `Network`, the IPv6 pool
  dedication columns are NULL.
* `security_group.py` ipset type change takes effect per Instance at next start. The management
  server sends `--subnets` and `ModifyRoutedSubnetCommand` only to agents that report the
  capability in their startup details; assign on an Instance running on an old host fails with a
  clear message rather than leaving a half-configured subnet.

## 15. Implementation

On branch `routed-subnets`, on top of `direct-routed-network`, one commit per part:

| commit | content |
|---|---|
| security_group.py: per-Instance IPv4 ipset becomes hash:net | the one-line change that lets a subnet join the set, on its own for review |
| schema and data model | columns on the four pool tables, `vm_id` on `as_number`, offering and announcement tables, `routed_subnet` resource type |
| offerings, pool purpose and IPv6 prefix dedication | Routed Subnet Offering APIs, `purpose` on pools, IPv6 prefix dedication APIs, API registration gating |
| assignment, release, listing and the announcer SPI | `RoutedSubnetManagerImpl`, `SubnetCarver`, `RouteAnnouncer`, per-endpoint records, reconciler and health task, NIC removal and account cleanup hooks, limits |
| host routes, ipsets and hot changes on KVM | `NicTO.routedSubnets`, `modifymacip.sh -r` with the address-derived metric, `--subnets` and `network_rules_vm_subnet` in the security group script, `ModifyRoutedSubnetCommand` |
| routed-subnets metadata for ConfigDrive and the VR | `routed-subnets` file |
| HTTP route announcer plugin with an ExaBGP daemon | `plugins/routing/http-announcer`, `exabgp-announcer.py`, README, `restartNetwork` hook |
| BGP mode with an AS number per Instance, and the mode switch | ASN lifecycle, `routed-subnets-bgp` file, `updateRoutedSubnet` |
| UI | §11 |
| Marvin helpers and smoke tests | `test/integration/smoke/test_routed_subnets.py` |

Unit tests: `SubnetCarverTest`, `RoutedSubnetManagerImplTest`, `HttpRouteAnnouncerTest`, the
daemon's `test_exabgp_announcer.py`, and the security group script's `test_security_group.py`
(ipset type, `--subnets`, hot add/remove).

## 16. Still to verify

Nothing in the design is open. What has not been exercised yet:

* **Lab.** The whole chain on real hosts: carve, host route with metric, ipset membership,
  ExaBGP announcement and reconciliation after an ExaBGP restart, anycast with two Instances on
  one host, and a BGP-mode Instance peering multihop with the upstream router from the metadata
  it was given.
* **Marvin.** `test_routed_subnets.py` was written against the simulator's behaviour for L3
  networks but not run here.
* **UI.** Written and reviewed, not built or linted on this machine (no Node runtime available).
* **Upgrade.** The schema changes on an existing 4.20+ database with Routed-mode pools in use.

## 17. Decision log

* 2026-11-02 — next hop is the NIC address; offerings carry optional v4 and v6 sizes; existing
  pool tables extended rather than new ones; announce failures leave the subnet Pending and
  `restartNetwork` re-announces; generic HTTP announcer with an ExaBGP reference daemon, endpoint
  strategy chosen by the admin; BGP mode takes peers from `bgp_peers` and one ASN per Instance
  from the AS-number ranges, upstream router configuration is the operator's; anycast co-location
  on one host handled with address-derived route metrics; resource limit only, no usage records.
* 2026-11-02 (later) — IPv6 floor raised from /80 to /64; `purpose` stays an explicit pool flag;
  per-endpoint announcement records instead of one flag; Shared networks without security groups
  have no host-side binding, accepted; health polling with a daemon instance id drives resync
  after an ExaBGP restart or outage.
* 2026-11-02 (UI and mode switch) — UI is part of v1 (§11): Network → Routed Subnets, NIC tab,
  offering section, zone tabs un-gated from Routed mode, limits row. Mode switch is an offering
  change (`updateRoutedSubnet offeringid=`), §6.11. ExaBGP 6 `on-exit keep;` noted; Ze checked:
  pre-alpha, AGPL, runs ExaBGP process scripts through a bridge, so the reference daemon carries
  over and a native backend waits for a stable Ze API.
* 2026-11-02 (implementation) — child rows carry their owner; the announcer SPI is per endpoint
  and announcers register through an extension registry; a separate `SubnetCarver` serves the
  Instance path for both families while Routed mode keeps its carver; ConfigDrive is not
  rebuilt on a running Instance, the metadata is current as of the last start; the IPv4 ipset
  change is its own first commit.
