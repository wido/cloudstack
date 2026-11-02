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
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetOffering;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetService;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "listRoutedSubnetOfferings",
        description = "Lists Routed Subnet Offerings.",
        responseObject = RoutedSubnetOfferingResponse.class,
        since = "24.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class ListRoutedSubnetOfferingsCmd extends BaseListCmd {

    @Inject
    private RoutedSubnetService routedSubnetService;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = RoutedSubnetOfferingResponse.class, description = "the id of the offering")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "the name of the offering")
    private String name;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "offerings available in this zone")
    private Long zoneId;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "offerings visible in this domain")
    private Long domainId;

    @Parameter(name = ApiConstants.MODE, type = CommandType.STRING, description = "Static or BGP")
    private String mode;

    @Parameter(name = ApiConstants.STATE, type = CommandType.STRING, description = "Active or Inactive; users only see Active offerings")
    private String state;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getMode() {
        return mode;
    }

    public String getState() {
        return state;
    }

    @Override
    public void execute() {
        try {
            Pair<List<? extends RoutedSubnetOffering>, Integer> result = routedSubnetService.listRoutedSubnetOfferings(this);
            List<RoutedSubnetOfferingResponse> responses = new ArrayList<>();
            for (RoutedSubnetOffering offering : result.first()) {
                RoutedSubnetOfferingResponse response = routedSubnetService.createRoutedSubnetOfferingResponse(offering);
                response.setObjectName("routedsubnetoffering");
                responses.add(response);
            }
            ListResponse<RoutedSubnetOfferingResponse> listResponse = new ListResponse<>();
            listResponse.setResponses(responses, result.second());
            listResponse.setResponseName(getCommandName());
            setResponseObject(listResponse);
        } catch (InvalidParameterValueException e) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, e.getMessage());
        } catch (CloudRuntimeException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}
