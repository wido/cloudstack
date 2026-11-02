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
import java.util.List;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponseWithAnnotations;
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnet;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = RoutedSubnet.class)
public class RoutedSubnetResponse extends BaseResponseWithAnnotations implements ControlledEntityResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of the routed subnet; with anycast, of this attachment of it")
    private String id;

    @SerializedName(ApiConstants.SUBNET)
    @Param(description = "the subnet in CIDR notation")
    private String subnet;

    @SerializedName(ApiConstants.ADDRESS_FAMILY)
    @Param(description = "4 or 6")
    private Integer addressFamily;

    @SerializedName(ApiConstants.MODE)
    @Param(description = "Static (the management server announces the route) or BGP (the Instance does)")
    private String mode;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "Allocated, or Releasing while an announcer endpoint still holds a withdrawn route")
    private String state;

    @SerializedName(ApiConstants.ANNOUNCED)
    @Param(description = "for Static mode: none, partial or all, by how many announcer endpoints hold the route; absent for BGP mode")
    private String announced;

    @SerializedName(ApiConstants.OFFERING_ID)
    @Param(description = "the id of the Routed Subnet Offering")
    private String offeringId;

    @SerializedName(ApiConstants.OFFERING_NAME)
    @Param(description = "the name of the Routed Subnet Offering")
    private String offeringName;

    @SerializedName(ApiConstants.NIC_ID)
    @Param(description = "the id of the NIC the subnet is routed to")
    private String nicId;

    @SerializedName(ApiConstants.NEXT_HOP)
    @Param(description = "the NIC address the subnet is routed to")
    private String nextHop;

    @SerializedName(ApiConstants.VIRTUAL_MACHINE_ID)
    @Param(description = "the id of the Instance the NIC belongs to")
    private String virtualMachineId;

    @SerializedName(ApiConstants.VIRTUAL_MACHINE_NAME)
    @Param(description = "the name of the Instance the NIC belongs to")
    private String virtualMachineName;

    @SerializedName(ApiConstants.NETWORK_ID)
    @Param(description = "the id of the NIC's network")
    private String networkId;

    @SerializedName(ApiConstants.NICS)
    @Param(description = "every NIC the subnet is routed to; more than one for an anycast subnet", responseObject = RoutedSubnetNicResponse.class)
    private List<RoutedSubnetNicResponse> nics;

    @SerializedName(ApiConstants.ENDPOINTS)
    @Param(description = "for Static mode: what each announcer endpoint has confirmed", responseObject = RoutedSubnetEndpointResponse.class)
    private List<RoutedSubnetEndpointResponse> endpoints;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the id of the zone")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the name of the zone")
    private String zoneName;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account owning the subnet")
    private String accountName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the id of the domain of the owner")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the name of the domain of the owner")
    private String domainName;

    @SerializedName(ApiConstants.DOMAIN_PATH)
    @Param(description = "the path of the domain of the owner", since = "4.19.2.0")
    private String domainPath;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the id of the project owning the subnet")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the name of the project owning the subnet")
    private String projectName;

    @SerializedName(ApiConstants.CREATED)
    @Param(description = "when the subnet was assigned")
    private Date created;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }

    public void setAddressFamily(Integer addressFamily) {
        this.addressFamily = addressFamily;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setAnnounced(String announced) {
        this.announced = announced;
    }

    public void setOfferingId(String offeringId) {
        this.offeringId = offeringId;
    }

    public void setOfferingName(String offeringName) {
        this.offeringName = offeringName;
    }

    public void setNicId(String nicId) {
        this.nicId = nicId;
    }

    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }

    public void setVirtualMachineId(String virtualMachineId) {
        this.virtualMachineId = virtualMachineId;
    }

    public void setVirtualMachineName(String virtualMachineName) {
        this.virtualMachineName = virtualMachineName;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public void setNics(List<RoutedSubnetNicResponse> nics) {
        this.nics = nics;
    }

    public void setEndpoints(List<RoutedSubnetEndpointResponse> endpoints) {
        this.endpoints = endpoints;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    @Override
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Override
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    @Override
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    @Override
    public void setDomainPath(String domainPath) {
        this.domainPath = domainPath;
    }

    @Override
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setCreated(Date created) {
        this.created = created;
    }
}
