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
import org.apache.cloudstack.api.command.admin.routedsubnet.UpdateRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ListRoutedSubnetOfferingsCmd;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

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

    ConfigKey<Integer> RoutedSubnetReconcileInterval = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.subnet.reconcile.interval", "300",
            "Seconds between runs of the task that makes every route announcer endpoint match the database; 0 disables it.",
            false);

    RoutedSubnetOffering createRoutedSubnetOffering(CreateRoutedSubnetOfferingCmd cmd);

    RoutedSubnetOffering updateRoutedSubnetOffering(UpdateRoutedSubnetOfferingCmd cmd);

    boolean deleteRoutedSubnetOffering(DeleteRoutedSubnetOfferingCmd cmd);

    Pair<List<? extends RoutedSubnetOffering>, Integer> listRoutedSubnetOfferings(ListRoutedSubnetOfferingsCmd cmd);

    RoutedSubnetOfferingResponse createRoutedSubnetOfferingResponse(RoutedSubnetOffering offering);
}
