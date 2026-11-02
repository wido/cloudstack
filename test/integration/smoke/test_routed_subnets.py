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
""" Tests for subnets routed to Instances: an IPv4 /30-or-larger and/or IPv6 /64-or-larger
    subnet carved from the zone's Instance-purpose pools and routed to the unicast address
    of a NIC on a Direct Routed (L3) network. BGP mode (the Instance announces, with its own
    AS number, and may share the subnet with other Instances of the account for anycast)
    needs no route announcer and is exercised here; Static mode needs one and is checked to
    be refused without.
"""
import ipaddress
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.lib.base import (Account,
                             ASNRange,
                             Configurations,
                             GuestNetworkIpv6Prefix,
                             Network,
                             NetworkOffering,
                             PhysicalNetwork,
                             Resources,
                             RoutedSubnet,
                             RoutedSubnetOffering,
                             ServiceOffering,
                             VirtualMachine,
                             Zone,
                             ZoneIpv4Subnet)
from marvin.lib.common import (get_domain,
                               get_template,
                               get_zone)
from marvin.lib.utils import cleanup_resources
from nose.plugins.attrib import attr

IPV4_POOL = "198.51.100.0/24"
IPV6_POOL = "2001:db8:100::/56"
ROUTED_SUBNET_RESOURCE_TYPE = 17


class TestRoutedSubnets(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        testClient = super(TestRoutedSubnets, cls).getClsTestClient()
        cls.apiclient = testClient.getApiClient()
        cls.services = testClient.getParsedTestDataConfig()
        cls.domain = get_domain(cls.apiclient)
        cls.zone = get_zone(cls.apiclient, testClient.getZoneForTests())
        cls.template = get_template(cls.apiclient, cls.zone.id, cls.services["ostype"])
        cls._cleanup = []
        cls.skip = False

        zone = Zone(cls.zone.__dict__)
        if zone.networktype != 'Advanced':
            cls.skip = True
            return

        cls.services["virtual_machine"]["zoneid"] = cls.zone.id
        cls.services["virtual_machine"]["template"] = cls.template.id

        cls.physical_network = PhysicalNetwork.create(
            cls.apiclient,
            {"name": "l3-routed-subnets"},
            zoneid=cls.zone.id,
            isolationmethods="ROUTED"
        )
        cls._cleanup.append(cls.physical_network)
        cls.physical_network.addTrafficType(cls.apiclient, "Guest")
        cls.physical_network.update(
            cls.apiclient,
            vlan="5900-5999",
            tags="l3routedsubnets",
            state="Enabled"
        )

        cls.service_offering = ServiceOffering.create(cls.apiclient, cls.services["service_offering"])
        cls._cleanup.append(cls.service_offering)

        l3_offering = dict(cls.services["l3_network_offering"])
        l3_offering["name"] = "Test L3 for routed subnets"
        l3_offering["tags"] = "l3routedsubnets"
        cls.network_offering = NetworkOffering.create(cls.apiclient, l3_offering)
        cls._cleanup.append(cls.network_offering)
        cls.network_offering.update(cls.apiclient, state='Enabled')

        cls.account = Account.create(cls.apiclient, cls.services["account"], admin=True, domainid=cls.domain.id)
        cls._cleanup.append(cls.account)

        # The pools the subnets are carved from: Instance purpose, so the Routed-mode and
        # Isolated-network allocators never touch them.
        cls.ipv4_pool = ZoneIpv4Subnet.create(cls.apiclient, cls.zone.id, IPV4_POOL, purpose="Instance")
        cls._cleanup.append(cls.ipv4_pool)
        cls.ipv6_pool = GuestNetworkIpv6Prefix.create(cls.apiclient, cls.zone.id, IPV6_POOL, purpose="Instance")
        cls._cleanup.append(cls.ipv6_pool)

        # BGP mode hands every Instance its own AS number from the zone's ranges
        cls.asn_range = ASNRange.create(cls.apiclient, cls.zone.id, 65000, 65010)
        cls._cleanup.append(cls.asn_range)

        cls.bgp_offering = RoutedSubnetOffering.create(cls.apiclient, cls.services["routed_subnet_offering_bgp"])
        cls._cleanup.append(cls.bgp_offering)
        cls.static_offering = RoutedSubnetOffering.create(cls.apiclient, cls.services["routed_subnet_offering_static"])
        cls._cleanup.append(cls.static_offering)

        # The network the Instances live on: an IPv4 and an IPv6 subnet routed to the hosts
        services = dict(cls.services["l3_network"])
        services["name"] = "Test L3 network for routed subnets"
        services["ip6cidr"] = "2001:db8:200::/64"
        cls.network = Network.create(
            cls.apiclient,
            services,
            zoneid=cls.zone.id,
            networkofferingid=cls.network_offering.id,
            accountid=cls.account.name,
            domainid=cls.account.domainid
        )
        cls._cleanup.append(cls.network)

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.apiclient, reversed(cls._cleanup))
        except Exception as e:
            raise Exception("Warning: Exception during class cleanup : %s" % e)

    def setUp(self):
        if self.skip:
            self.skipTest("Routed subnets require an Advanced zone, skipping")
        self.cleanup = []

    def tearDown(self):
        try:
            cleanup_resources(self.apiclient, reversed(self.cleanup))
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)

    def deploy_vm(self):
        virtual_machine = VirtualMachine.create(
            self.apiclient,
            self.services["virtual_machine"],
            accountid=self.account.name,
            domainid=self.account.domainid,
            serviceofferingid=self.service_offering.id,
            networkids=[self.network.id]
        )
        self.cleanup.append(virtual_machine)
        self.assertEqual(virtual_machine.state, "Running")
        return virtual_machine

    def assign(self, vm, **kwargs):
        subnets = RoutedSubnet.assign(self.apiclient, vm.nic[0].id, **kwargs)
        for subnet in subnets:
            self.cleanup.append(subnet)
        return subnets

    def list_for_nic(self, vm):
        return RoutedSubnet.list(self.apiclient, nicid=vm.nic[0].id, listall=True) or []

    def allocated_asn_of(self, vm):
        numbers = ASNRange.listAsNumbers(self.apiclient, zoneid=self.zone.id, allocated=True) or []
        return [n for n in numbers if getattr(n, "virtualmachineid", None) == vm.id]

    @attr(tags=["advanced", "smoke"], required_hardware="false")
    def test_01_offering_bounds(self):
        """ An offering hands out /30 .. zone shortest for IPv4 and /64 .. zone shortest for
            IPv6; anything outside, or an offering without any family, is refused. """
        for services in [
            {"name": "too small v4", "mode": "BGP", "ip4prefixlength": 31},
            {"name": "too small v6", "mode": "BGP", "ip6prefixlength": 65},
            {"name": "too large v4", "mode": "BGP", "ip4prefixlength": 8},
            {"name": "no family", "mode": "BGP"},
            {"name": "bad mode", "mode": "Dynamic", "ip4prefixlength": 29},
        ]:
            with self.assertRaises(Exception, msg="offering %s must be refused" % services["name"]):
                offering = RoutedSubnetOffering.create(self.apiclient, services)
                self.cleanup.append(offering)

        listed = RoutedSubnetOffering.list(self.apiclient, id=self.bgp_offering.id)
        self.assertEqual(1, len(listed))
        self.assertEqual("BGP", listed[0].mode)
        self.assertEqual(29, listed[0].ip4prefixlength)
        self.assertEqual(64, listed[0].ip6prefixlength)

    @attr(tags=["advanced", "smoke"], required_hardware="false")
    def test_02_assign_bgp_subnets(self):
        """ A dual offering routes one IPv4 and one IPv6 subnet to the NIC, each carved from an
            Instance-purpose pool of the zone, and the Instance gets an AS number. """
        vm = self.deploy_vm()
        subnets = self.assign(vm, offeringid=self.bgp_offering.id)
        self.assertEqual(2, len(subnets), "a /29 and a /64 were expected")

        by_family = {s.addressfamily: s for s in subnets}
        v4 = ipaddress.ip_network(by_family[4].subnet)
        v6 = ipaddress.ip_network(by_family[6].subnet)
        self.assertEqual(29, v4.prefixlen)
        self.assertTrue(v4.subnet_of(ipaddress.ip_network(IPV4_POOL)), "the IPv4 subnet must come from the pool")
        self.assertEqual(64, v6.prefixlen)
        self.assertTrue(v6.subnet_of(ipaddress.ip_network(IPV6_POOL)), "the IPv6 subnet must come from the prefix")
        for subnet in subnets:
            self.assertEqual("BGP", subnet.mode)
            self.assertEqual("Allocated", subnet.state)
            self.assertEqual(vm.id, subnet.virtualmachineid)
            self.assertIsNone(getattr(subnet, "announced", None), "BGP-mode subnets are not announced by CloudStack")
        self.assertEqual(vm.nic[0].ipaddress, by_family[4].nexthop)
        self.assertEqual(vm.nic[0].ip6address, by_family[6].nexthop)

        listed = self.list_for_nic(vm)
        self.assertEqual(2, len(listed))

        asns = self.allocated_asn_of(vm)
        self.assertEqual(1, len(asns), "the Instance must hold exactly one AS number")
        self.assertTrue(65000 <= asns[0].asnumber <= 65010)

    @attr(tags=["advanced", "smoke"], required_hardware="false")
    def test_03_anycast_and_release(self):
        """ A BGP-mode subnet can be routed to a second Instance of the same account; each
            Instance keeps its own AS number. Releasing it from one Instance leaves the other,
            releasing it from the last returns the subnet to the pool and the AS number. """
        vm1 = self.deploy_vm()
        vm2 = self.deploy_vm()
        subnets = self.assign(vm1, offeringid=self.bgp_offering.id)
        v4 = [s for s in subnets if s.addressfamily == 4][0]

        attached = self.assign(vm2, subnetid=v4.id)
        self.assertEqual(1, len(attached))
        self.assertEqual(v4.subnet, attached[0].subnet, "the same subnet is routed to the second Instance")
        self.assertEqual(vm2.id, attached[0].virtualmachineid)

        details = RoutedSubnet.list(self.apiclient, id=v4.id, listall=True)[0]
        self.assertEqual(2, len(details.nics), "both Instances must be listed on the subnet")
        self.assertEqual({vm1.id, vm2.id}, {n.virtualmachineid for n in details.nics})
        self.assertNotEqual(self.allocated_asn_of(vm1)[0].asnumber, self.allocated_asn_of(vm2)[0].asnumber,
                            "anycast members speak with distinct AS numbers")

        # a second attachment to the same NIC is refused
        with self.assertRaises(Exception):
            RoutedSubnet.assign(self.apiclient, vm2.nic[0].id, subnetid=v4.id)

        attached[0].release(self.apiclient, nicid=vm2.nic[0].id)
        self.cleanup.remove(attached[0])
        self.assertEqual(0, len(self.list_for_nic(vm2)))
        self.assertEqual(0, len(self.allocated_asn_of(vm2)), "the AS number goes back with the last BGP subnet")
        self.assertEqual(2, len(self.list_for_nic(vm1)), "the first Instance keeps its subnets")

        for subnet in subnets:
            subnet.release(self.apiclient)
            self.cleanup.remove(subnet)
        self.assertEqual(0, len(self.list_for_nic(vm1)))
        self.assertEqual(0, len(self.allocated_asn_of(vm1)))

        # the subnet is Free again and the next assignment reuses it
        again = self.assign(vm1, offeringid=self.bgp_offering.id)
        self.assertEqual(v4.subnet, [s for s in again if s.addressfamily == 4][0].subnet,
                         "a freed subnet of the right size is reused before a new one is carved")

    @attr(tags=["advanced", "smoke"], required_hardware="false")
    def test_04_static_mode_needs_an_announcer(self):
        """ Static mode is announced by the management server through the zone's route
            announcer; without one configured the assignment is refused up front. """
        Configurations.update(self.apiclient, name="routed.subnet.announcer", value="", zoneid=self.zone.id)
        vm = self.deploy_vm()
        with self.assertRaises(Exception):
            RoutedSubnet.assign(self.apiclient, vm.nic[0].id, offeringid=self.static_offering.id)
        self.assertEqual(0, len(self.list_for_nic(vm)))

    @attr(tags=["advanced", "smoke"], required_hardware="false")
    def test_05_resource_limit(self):
        """ Subnets count against the account's routed_subnet limit, per subnet. """
        Resources.updateLimit(self.apiclient, resourcetype=ROUTED_SUBNET_RESOURCE_TYPE, max=1,
                              account=self.account.name, domainid=self.account.domainid)
        try:
            vm = self.deploy_vm()
            with self.assertRaises(Exception, msg="a dual offering needs two subnets, the limit allows one"):
                self.assign(vm, offeringid=self.bgp_offering.id)
            self.assertEqual(0, len(self.list_for_nic(vm)), "nothing stays allocated after a refused assignment")
        finally:
            Resources.updateLimit(self.apiclient, resourcetype=ROUTED_SUBNET_RESOURCE_TYPE, max=-1,
                                  account=self.account.name, domainid=self.account.domainid)

    @attr(tags=["advanced", "smoke"], required_hardware="false")
    def test_06_subnets_go_with_the_instance(self):
        """ Expunging an Instance releases its subnets and its AS number. """
        vm = self.deploy_vm()
        subnets = self.assign(vm, offeringid=self.bgp_offering.id)
        for subnet in subnets:
            self.cleanup.remove(subnet)
        vm.delete(self.apiclient, expunge=True)
        self.cleanup.remove(vm)

        remaining = RoutedSubnet.list(self.apiclient, account=self.account.name, domainid=self.account.domainid, listall=True) or []
        self.assertEqual(0, len([s for s in remaining if s.virtualmachineid == vm.id]))
        self.assertEqual(0, len(self.allocated_asn_of(vm)))
