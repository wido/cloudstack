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
package org.apache.cloudstack.api.command.admin.routedsubnet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetOffering;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetService;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.Account;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "createRoutedSubnetOffering",
        description = "Creates a Routed Subnet Offering: the sizes of the IPv4 and/or IPv6 subnet an Instance gets routed to its NIC, and who announces the route.",
        responseObject = RoutedSubnetOfferingResponse.class,
        since = "24.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin})
public class CreateRoutedSubnetOfferingCmd extends BaseCmd {

    @Inject
    private RoutedSubnetService routedSubnetService;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the offering")
    private String name;

    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, description = "the display text of the offering, defaults to the name")
    private String displayText;

    @Parameter(name = ApiConstants.IP4_PREFIX_LENGTH, type = CommandType.INTEGER,
            description = "prefix length of the IPv4 subnet to hand out, between the zone's shortest allowed prefix and /" + RoutedSubnetOffering.IPV4_LONGEST_PREFIX + "; omit for no IPv4 subnet")
    private Integer ip4PrefixLength;

    @Parameter(name = ApiConstants.IP6_PREFIX_LENGTH, type = CommandType.INTEGER,
            description = "prefix length of the IPv6 subnet to hand out, between the zone's shortest allowed prefix and /" + RoutedSubnetOffering.IPV6_LONGEST_PREFIX + "; omit for no IPv6 subnet")
    private Integer ip6PrefixLength;

    @Parameter(name = ApiConstants.MODE, type = CommandType.STRING, required = true,
            description = "who announces the route: Static (the management server, through the zone's route announcer) or BGP (the Instance itself, with its own AS number)")
    private String mode;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "limit the offering to this zone")
    private Long zoneId;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "make the offering visible in this domain (and its children) only")
    private Long domainId;

    @Parameter(name = ApiConstants.DETAILS, type = CommandType.MAP,
            description = "key/value details, for example bgp.peers=<comma separated BGP peer ids> to hand BGP-mode Instances only those peers")
    private Map details;

    public String getName() {
        return name;
    }

    public String getDisplayText() {
        return displayText == null ? name : displayText;
    }

    public Integer getIp4PrefixLength() {
        return ip4PrefixLength;
    }

    public Integer getIp6PrefixLength() {
        return ip6PrefixLength;
    }

    public String getMode() {
        return mode;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getDomainId() {
        return domainId;
    }

    public Map<String, String> getDetails() {
        Map<String, String> result = new HashMap<>();
        if (details == null || details.isEmpty()) {
            return result;
        }
        Collection<?> values = details.values();
        for (Object value : values) {
            if (value instanceof Map) {
                Map<?, ?> entry = (Map<?, ?>) value;
                Object key = entry.get("key");
                Object val = entry.get("value");
                if (key != null) {
                    result.put(key.toString(), val == null ? null : val.toString());
                }
            }
        }
        return result;
    }

    @Override
    public void execute() {
        try {
            RoutedSubnetOffering offering = routedSubnetService.createRoutedSubnetOffering(this);
            if (offering == null) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create the Routed Subnet Offering");
            }
            RoutedSubnetOfferingResponse response = routedSubnetService.createRoutedSubnetOfferingResponse(offering);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (InvalidParameterValueException e) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, e.getMessage());
        } catch (CloudRuntimeException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
