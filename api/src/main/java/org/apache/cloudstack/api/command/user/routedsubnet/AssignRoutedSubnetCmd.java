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
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NicResponse;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.api.response.RoutedSubnetResponse;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnet;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetService;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.Nic;
import com.cloud.vm.VirtualMachine;

@APICommand(name = "assignRoutedSubnet",
        description = "Routes a subnet to the unicast address of an Instance's NIC. Either an offering, which carves new subnet(s) from the zone pools, or an existing BGP-mode subnet of the account to attach to another NIC for anycast.",
        responseObject = RoutedSubnetResponse.class,
        since = "24.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class AssignRoutedSubnetCmd extends BaseAsyncCmd {

    @Inject
    private RoutedSubnetService routedSubnetService;

    @Parameter(name = ApiConstants.NIC_ID, type = CommandType.UUID, entityType = NicResponse.class, required = true, description = "the NIC whose address the subnet is routed to")
    private Long nicId;

    @Parameter(name = ApiConstants.OFFERING_ID, type = CommandType.UUID, entityType = RoutedSubnetOfferingResponse.class, description = "the Routed Subnet Offering to assign; one of offeringid and subnetid is required")
    private Long offeringId;

    @Parameter(name = ApiConstants.SUBNET_ID, type = CommandType.STRING, description = "an existing BGP-mode routed subnet of the account to attach to this NIC as well (anycast)")
    private String subnetId;

    @Parameter(name = ApiConstants.IP4_SUBNET, type = CommandType.STRING, authorized = {RoleType.Admin}, description = "the exact IPv4 subnet to assign, which must be free in a pool the account may use; root admin only")
    private String ip4Subnet;

    @Parameter(name = ApiConstants.IP6_SUBNET, type = CommandType.STRING, authorized = {RoleType.Admin}, description = "the exact IPv6 subnet to assign, which must be free in a pool the account may use; root admin only")
    private String ip6Subnet;

    public Long getNicId() {
        return nicId;
    }

    public Long getOfferingId() {
        return offeringId;
    }

    public String getSubnetId() {
        return subnetId;
    }

    public String getIp4Subnet() {
        return ip4Subnet;
    }

    public String getIp6Subnet() {
        return ip6Subnet;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_ROUTED_SUBNET_ASSIGN;
    }

    @Override
    public String getEventDescription() {
        return "Routing a subnet to NIC " + getResourceUuid(ApiConstants.NIC_ID);
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.VirtualMachine;
    }

    @Override
    public Long getApiResourceId() {
        Nic nic = _entityMgr.findById(Nic.class, nicId);
        return nic == null ? null : nic.getInstanceId();
    }

    @Override
    public void execute() throws ResourceAllocationException {
        try {
            List<RoutedSubnet> subnets = routedSubnetService.assignRoutedSubnet(this);
            List<RoutedSubnetResponse> responses = new ArrayList<>();
            for (RoutedSubnet subnet : subnets) {
                responses.add(routedSubnetService.createRoutedSubnetResponse(subnet));
            }
            ListResponse<RoutedSubnetResponse> response = new ListResponse<>();
            response.setResponses(responses, responses.size());
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
        Nic nic = _entityMgr.findById(Nic.class, nicId);
        if (nic == null) {
            throw new InvalidParameterValueException("Unable to find NIC with id " + nicId);
        }
        VirtualMachine vm = _entityMgr.findById(VirtualMachine.class, nic.getInstanceId());
        return vm == null ? -1 : vm.getAccountId();
    }
}
