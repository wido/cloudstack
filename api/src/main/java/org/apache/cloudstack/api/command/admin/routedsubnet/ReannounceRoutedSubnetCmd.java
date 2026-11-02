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

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.RoutedSubnetResponse;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnet;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetService;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.Account;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "reannounceRoutedSubnet",
        description = "Makes every announcer endpoint of the zone hold the route of a Static-mode routed subnet again, for example after a speaker was replaced.",
        responseObject = RoutedSubnetResponse.class,
        since = "24.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin})
public class ReannounceRoutedSubnetCmd extends BaseAsyncCmd {

    @Inject
    private RoutedSubnetService routedSubnetService;

    @Parameter(name = ApiConstants.ID, type = CommandType.STRING, required = true, description = "the id of the routed subnet")
    private String id;

    public String getId() {
        return id;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_ROUTED_SUBNET_REANNOUNCE;
    }

    @Override
    public String getEventDescription() {
        return "Re-announcing routed subnet " + id;
    }

    @Override
    public void execute() {
        try {
            RoutedSubnet subnet = routedSubnetService.reannounceRoutedSubnet(this);
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
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
