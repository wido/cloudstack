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

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

/** One NIC a routed subnet is routed to; several for an anycast subnet. */
public class RoutedSubnetNicResponse extends BaseResponse {

    @SerializedName(ApiConstants.ID)
    @Param(description = "the id of this attachment of the subnet to the NIC")
    private String id;

    @SerializedName(ApiConstants.NIC_ID)
    @Param(description = "the id of the NIC")
    private String nicId;

    @SerializedName(ApiConstants.VIRTUAL_MACHINE_ID)
    @Param(description = "the id of the Instance the NIC belongs to")
    private String virtualMachineId;

    @SerializedName(ApiConstants.VIRTUAL_MACHINE_NAME)
    @Param(description = "the name of the Instance the NIC belongs to")
    private String virtualMachineName;

    @SerializedName(ApiConstants.NETWORK_ID)
    @Param(description = "the id of the NIC's network")
    private String networkId;

    @SerializedName(ApiConstants.NEXT_HOP)
    @Param(description = "the NIC address the subnet is routed to")
    private String nextHop;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "Allocated or Releasing")
    private String state;

    @SerializedName(ApiConstants.ANNOUNCED)
    @Param(description = "for Static mode: none, partial or all, by how many announcer endpoints hold the route; absent for BGP mode")
    private String announced;

    public void setId(String id) {
        this.id = id;
    }

    public void setNicId(String nicId) {
        this.nicId = nicId;
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

    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setAnnounced(String announced) {
        this.announced = announced;
    }
}
