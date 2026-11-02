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

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.api.response.RoutedSubnetResponse;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnet;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetService;

import org.apache.cloudstack.context.CallContext;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "updateRoutedSubnet",
        description = "Moves a routed subnet to another Routed Subnet Offering of the same family and size. Since the offering fixes who announces the route, this is how a subnet switches between Static and BGP mode. Bring the Instance's BGP session up before switching to BGP; release anycast members down to one before switching to Static.",
        responseObject = RoutedSubnetResponse.class,
        since = "24.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class UpdateRoutedSubnetCmd extends BaseAsyncCmd {

    @Inject
    private RoutedSubnetService routedSubnetService;

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING, required = true, description = "the id of the routed subnet")
    private String id;

    @Parameter(name = ApiConstants.OFFERING_ID, type = CommandType.UUID, entityType = RoutedSubnetOfferingResponse.class, required = true, description = "the offering to move the subnet to")
    private Long offeringId;

    public String getId() {
        return id;
    }

    public Long getOfferingId() {
        return offeringId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_ROUTED_SUBNET_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Changing the offering of routed subnet " + id;
    }

    @Override
    public void execute() {
        try {
            RoutedSubnet subnet = routedSubnetService.updateRoutedSubnet(this);
            RoutedSubnetResponse response = routedSubnetService.createRoutedSubnetResponse(subnet);
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
        return CallContext.current().getCallingAccountId();
    }
}
