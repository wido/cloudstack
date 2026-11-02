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
package org.apache.cloudstack.api.command.admin.network;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.DataCenterGuestIpv6PrefixResponse;

import com.cloud.dc.DataCenterGuestIpv6Prefix;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.Account;
import com.cloud.utils.exception.CloudRuntimeException;

@APICommand(name = "releaseDedicatedGuestNetworkIpv6Prefix",
        description = "Releases the dedication of a guest network IPv6 prefix, making it available zone-wide again.",
        responseObject = DataCenterGuestIpv6PrefixResponse.class,
        since = "24.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        authorized = {RoleType.Admin})
public class ReleaseDedicatedGuestNetworkIpv6PrefixCmd extends BaseAsyncCmd {

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = DataCenterGuestIpv6PrefixResponse.class, required = true, description = "id of the guest network IPv6 prefix")
    private Long id;

    public Long getId() {
        return id;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_GUEST_IP6_PREFIX_RELEASE;
    }

    @Override
    public String getEventDescription() {
        return "Releasing dedicated guest IPv6 prefix with ID: " + getResourceUuid(ApiConstants.ID);
    }

    @Override
    public void execute() {
        try {
            DataCenterGuestIpv6Prefix result = _configService.releaseDedicatedDataCenterGuestIpv6Prefix(this);
            if (result == null) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to release dedicated guest network IPv6 prefix: " + getId());
            }
            DataCenterGuestIpv6PrefixResponse response = _responseGenerator.createDataCenterGuestIpv6PrefixResponse(result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (InvalidParameterValueException ex) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, ex.getMessage());
        } catch (CloudRuntimeException ex) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
