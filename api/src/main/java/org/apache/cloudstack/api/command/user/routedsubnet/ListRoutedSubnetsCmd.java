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
package org.apache.cloudstack.api.command.user.routedsubnet;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListProjectAndAccountResourcesCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NicResponse;
import org.apache.cloudstack.api.response.RoutedSubnetResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnet;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetService;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "listRoutedSubnets",
        description = "Lists subnets routed to Instances.",
        responseObject = RoutedSubnetResponse.class,
        since = "24.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class ListRoutedSubnetsCmd extends BaseListProjectAndAccountResourcesCmd {

    @Inject
    private RoutedSubnetService routedSubnetService;

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING, description = "the id of a routed subnet")
    private String id;

    @Parameter(name = ApiConstants.NIC_ID, type = CommandType.UUID, entityType = NicResponse.class, description = "subnets routed to this NIC")
    private Long nicId;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID, type = CommandType.UUID, entityType = UserVmResponse.class, description = "subnets routed to any NIC of this Instance")
    private Long virtualMachineId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "subnets in this zone")
    private Long zoneId;

    @Parameter(name = ApiConstants.MODE, type = CommandType.STRING, description = "Static or BGP")
    private String mode;

    @Parameter(name = ApiConstants.SUBNET, type = CommandType.STRING, description = "the exact subnet in CIDR notation")
    private String subnet;

    public String getId() {
        return id;
    }

    public Long getNicId() {
        return nicId;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public String getMode() {
        return mode;
    }

    public String getSubnet() {
        return subnet;
    }

    @Override
    public void execute() {
        try {
            Pair<List<RoutedSubnet>, Integer> result = routedSubnetService.listRoutedSubnets(this);
            List<RoutedSubnetResponse> responses = new ArrayList<>();
            for (RoutedSubnet subnet : result.first()) {
                responses.add(routedSubnetService.createRoutedSubnetResponse(subnet));
            }
            ListResponse<RoutedSubnetResponse> response = new ListResponse<>();
            response.setResponses(responses, result.second());
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (InvalidParameterValueException e) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, e.getMessage());
        } catch (CloudRuntimeException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}
