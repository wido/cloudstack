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
package org.apache.cloudstack.api.response;

import java.util.Date;
import java.util.Map;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetOffering;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = RoutedSubnetOffering.class)
public class RoutedSubnetOfferingResponse extends BaseResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the Routed Subnet Offering")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "the name of the Routed Subnet Offering")
    private String name;

    @SerializedName(ApiConstants.DISPLAY_TEXT)
    @Param(description = "the display text of the Routed Subnet Offering")
    private String displayText;

    @SerializedName(ApiConstants.IP4_PREFIX_LENGTH)
    @Param(description = "the prefix length of the IPv4 subnet the offering hands out; absent when it hands out no IPv4 subnet")
    private Integer ip4PrefixLength;

    @SerializedName(ApiConstants.IP6_PREFIX_LENGTH)
    @Param(description = "the prefix length of the IPv6 subnet the offering hands out; absent when it hands out no IPv6 subnet")
    private Integer ip6PrefixLength;

    @SerializedName(ApiConstants.MODE)
    @Param(description = "who announces the route: Static (the management server, through the zone's route announcer) or BGP (the Instance itself)")
    private String mode;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the state of the Routed Subnet Offering: Active or Inactive")
    private String state;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the zone the offering is limited to, absent when it is available in every zone")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the name of the zone the offering is limited to")
    private String zoneName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the domain the offering is visible in, absent when it is public")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the name of the domain the offering is visible in")
    private String domainName;

    @SerializedName(ApiConstants.DETAILS)
    @Param(description = "additional key/value details of the offering")
    private Map<String, String> details;

    @SerializedName(ApiConstants.CREATED)
    @Param(description = "the date this offering was created")
    private Date created;

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    public void setIp4PrefixLength(Integer ip4PrefixLength) {
        this.ip4PrefixLength = ip4PrefixLength;
    }

    public void setIp6PrefixLength(Integer ip6PrefixLength) {
        this.ip6PrefixLength = ip6PrefixLength;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getMode() {
        return mode;
    }
}
