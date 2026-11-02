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

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

/** What one announcer endpoint has confirmed about a routed subnet. */
public class RoutedSubnetEndpointResponse extends BaseResponse {

    @SerializedName(ApiConstants.ENDPOINT)
    @Param(description = "the announcer endpoint")
    private String endpoint;

    @SerializedName(ApiConstants.ANNOUNCED)
    @Param(description = "when the endpoint confirmed it holds the route; absent while it does not")
    private Date announced;

    @SerializedName(ApiConstants.LAST_ATTEMPT)
    @Param(description = "when the management server last tried this endpoint")
    private Date lastAttempt;

    @SerializedName(ApiConstants.LAST_ERROR)
    @Param(description = "the error of the last failed attempt, absent after a success")
    private String lastError;

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setAnnounced(Date announced) {
        this.announced = announced;
    }

    public void setLastAttempt(Date lastAttempt) {
        this.lastAttempt = lastAttempt;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
