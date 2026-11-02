// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.cloudstack.api.response;

import java.util.Date;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.dc.DataCenterGuestIpv6Prefix;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = DataCenterGuestIpv6Prefix.class)
public class DataCenterGuestIpv6PrefixResponse extends BaseResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the guest IPv6 prefix")
    private String id;

    @SerializedName(ApiConstants.PREFIX)
    @Param(description = "Guest IPv6 prefix")
    private String prefix;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "ID of zone to which the IPv6 prefix belongs to." )
    private String zoneId;

    @SerializedName(ApiConstants.USED_SUBNETS)
    @Param(description = "Count of the used IPv6 subnets for the prefix." )
    private Integer usedSubnets;

    @SerializedName(ApiConstants.AVAILABLE_SUBNETS)
    @Param(description = "Count of the available IPv6 subnets for the prefix." )
    private Integer availableSubnets;

    @SerializedName(ApiConstants.TOTAL_SUBNETS)
    @Param(description = "Count of the total IPv6 subnets for the prefix." )
    private Integer totalSubnets;

    @SerializedName(ApiConstants.CREATED)
    @Param(description = "Date when this IPv6 prefix was created." )
    private Date created;

    @SerializedName(ApiConstants.PURPOSE)
    @Param(description = "what the subnets carved from the prefix are for: Network or Instance", since = "24.0.0")
    private String purpose;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account the prefix is dedicated to", since = "24.0.0")
    private String accountName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the id of the domain the prefix is dedicated to", since = "24.0.0")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the name of the domain the prefix is dedicated to", since = "24.0.0")
    private String domainName;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the id of the project the prefix is dedicated to", since = "24.0.0")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the name of the project the prefix is dedicated to", since = "24.0.0")
    private String projectName;

    public void setId(String id) {
        this.id = id;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public void setUsedSubnets(Integer usedSubnets) {
        this.usedSubnets = usedSubnets;
    }

    public void setAvailableSubnets(Integer availableSubnets) {
        this.availableSubnets = availableSubnets;
    }

    public void setTotalSubnets(Integer totalSubnets) {
        this.totalSubnets = totalSubnets;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}
