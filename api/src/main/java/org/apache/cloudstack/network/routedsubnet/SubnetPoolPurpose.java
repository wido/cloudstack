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
package org.apache.cloudstack.network.routedsubnet;

/**
 * What the subnets carved from a zone pool (an IPv4 guest subnet or an IPv6 guest prefix) are
 * for. A pool serves one purpose, so the allocator for Routed-mode guest networks and the one
 * for subnets routed to Instances never carve from the same prefix.
 */
public enum SubnetPoolPurpose {
    /** Routed-mode guest networks and VPCs (the 4.20 behaviour, and the default). */
    Network,
    /** Subnets routed to an Instance's NIC address. */
    Instance;

    public static SubnetPoolPurpose fromString(String value) {
        if (value == null || value.isBlank()) {
            return Network;
        }
        for (SubnetPoolPurpose purpose : values()) {
            if (purpose.name().equalsIgnoreCase(value.trim())) {
                return purpose;
            }
        }
        throw new IllegalArgumentException("Unknown subnet pool purpose: " + value);
    }
}
