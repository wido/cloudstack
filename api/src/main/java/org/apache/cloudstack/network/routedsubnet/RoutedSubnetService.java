//Licensed to the Apache Software Foundation (ASF) under one
//or more contributor license agreements.  See the NOTICE file
//distributed with this work for additional information
//regarding copyright ownership.  The ASF licenses this file
//to you under the Apache License, Version 2.0 (the
//"License"); you may not use this file except in compliance
//the License.  You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,
//software distributed under the License is distributed on an
//"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//KIND, either express or implied.  See the License for the
//specific language governing permissions and limitations
//under the License.
package org.apache.cloudstack.network.routedsubnet;

import java.util.List;

import org.apache.cloudstack.api.command.admin.routedsubnet.CreateRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.DeleteRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.ReannounceRoutedSubnetCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.UpdateRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.AssignRoutedSubnetCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ListRoutedSubnetOfferingsCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ListRoutedSubnetsCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ReleaseRoutedSubnetCmd;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.api.response.RoutedSubnetResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.api.command.user.routedsubnet.UpdateRoutedSubnetCmd;

import com.cloud.exception.ResourceAllocationException;
import com.cloud.utils.Pair;
import com.cloud.utils.component.PluggableService;

/**
 * Subnets routed to the unicast address of an Instance's NIC: offerings, pools, assignment and
 * announcement. See docs/design/routed-subnets.md.
 */
public interface RoutedSubnetService extends PluggableService, Configurable {

    ConfigKey<Boolean> RoutedSubnetsEnabled = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Boolean.class,
            "routed.subnet.enabled", "true",
            "Whether subnets can be routed to Instances in the zone. The pool, AS number and BGP peer APIs are registered when this or routed.network.vpc.enabled is true.",
            true, ConfigKey.Scope.Zone);

    ConfigKey<Integer> RoutedSubnetIpv4ShortestPrefix = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.subnet.ipv4.shortest.prefix", "24",
            "The shortest IPv4 prefix (largest subnet) a Routed Subnet Offering may hand out in the zone; the longest is always /" + RoutedSubnetOffering.IPV4_LONGEST_PREFIX + ".",
            true, ConfigKey.Scope.Zone);

    ConfigKey<Integer> RoutedSubnetIpv6ShortestPrefix = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.subnet.ipv6.shortest.prefix", "56",
            "The shortest IPv6 prefix (largest subnet) a Routed Subnet Offering may hand out in the zone; the longest is always /" + RoutedSubnetOffering.IPV6_LONGEST_PREFIX + ".",
            true, ConfigKey.Scope.Zone);

    ConfigKey<String> RoutedSubnetAnnouncer = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, String.class,
            "routed.subnet.announcer", "",
            "Name of the route announcer plugin that gets Static-mode routed subnets into the fabric for this zone. Empty disables Static-mode offerings in the zone.",
            true, ConfigKey.Scope.Zone);

    ConfigKey<String> RoutedSubnetAnnouncerStrategy = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, String.class,
            "routed.subnet.announcer.strategy", "all",
            "How the announcer's endpoints are used: 'all' sends every announcement to every endpoint, 'failover' uses the first endpoint that accepts.",
            true, ConfigKey.Scope.Zone);

    ConfigKey<Integer> RoutedSubnetAnnouncerHealthInterval = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.subnet.announcer.health.interval", "30",
            "Seconds between health checks of the announcer endpoints; an endpoint that comes back or restarted is resynchronised at once. 0 disables the checks.",
            false);

    ConfigKey<Integer> RoutedSubnetReconcileInterval = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.subnet.reconcile.interval", "300",
            "Seconds between runs of the task that makes every route announcer endpoint match the database; 0 disables it.",
            false);

    String STRATEGY_ALL = "all";
    String STRATEGY_FAILOVER = "failover";

    // offerings

    RoutedSubnetOffering createRoutedSubnetOffering(CreateRoutedSubnetOfferingCmd cmd);

    RoutedSubnetOffering updateRoutedSubnetOffering(UpdateRoutedSubnetOfferingCmd cmd);

    boolean deleteRoutedSubnetOffering(DeleteRoutedSubnetOfferingCmd cmd);

    Pair<List<? extends RoutedSubnetOffering>, Integer> listRoutedSubnetOfferings(ListRoutedSubnetOfferingsCmd cmd);

    RoutedSubnetOfferingResponse createRoutedSubnetOfferingResponse(RoutedSubnetOffering offering);

    // subnets

    /** Assigns the offering's subnet(s), or attaches an existing BGP-mode subnet to another NIC. */
    List<RoutedSubnet> assignRoutedSubnet(AssignRoutedSubnetCmd cmd) throws ResourceAllocationException;

    boolean releaseRoutedSubnet(ReleaseRoutedSubnetCmd cmd);

    Pair<List<RoutedSubnet>, Integer> listRoutedSubnets(ListRoutedSubnetsCmd cmd);

    RoutedSubnet reannounceRoutedSubnet(ReannounceRoutedSubnetCmd cmd);

    /** Moves the subnet to another offering of the same family and size; the mode switch. */
    RoutedSubnet updateRoutedSubnet(UpdateRoutedSubnetCmd cmd);

    RoutedSubnetResponse createRoutedSubnetResponse(RoutedSubnet subnet);

    // internal

    /** The subnets routed to the NIC, both families. */
    List<RoutedSubnet> listRoutedSubnetsForNic(long nicId);

    /** Releases every subnet routed to the NIC; called when the NIC goes away. Never throws. */
    void releaseRoutedSubnetsForNic(long nicId);

    /** Releases every subnet of the account; called from account cleanup. Never throws. */
    void releaseRoutedSubnetsForAccount(long accountId);

    /** Re-announces every Static-mode subnet on the network's NICs; called from network restart. */
    void reannounceRoutedSubnetsForNetwork(long networkId);

    /**
     * The routed-subnets metadata file of an Instance: one line per subnet routed to any of its
     * NICs, "<cidr> <mode> <nic mac>", or an empty string when it has none.
     */
    String getRoutedSubnetsMetadata(long vmId);

    /**
     * The routed-subnets-bgp metadata file of an Instance with BGP-mode subnets: its AS number
     * as "local_asn=<asn>" and one "peer=<address> <asn> [password]" line per peer it should
     * talk to, or an empty string when it has no BGP-mode subnet.
     */
    String getRoutedSubnetsBgpMetadata(long vmId);
}
