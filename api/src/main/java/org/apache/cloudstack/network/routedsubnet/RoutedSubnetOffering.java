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

import java.util.Date;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

/**
 * An offering a user picks to get a subnet routed to a NIC. It fixes the address families, the
 * prefix lengths and who announces the route; users never choose a size themselves.
 */
public interface RoutedSubnetOffering extends InternalIdentity, Identity {

    enum Mode {
        /** The management server announces the route through the zone's route announcer. */
        Static,
        /** The Instance announces the route itself over BGP, with its own AS number. */
        BGP;

        public static Mode fromString(String value) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown routed subnet offering mode: " + value);
        }
    }

    enum State {
        Active, Inactive
    }

    /** Hard floors: nothing smaller is ever handed out, whatever the offering says. */
    int IPV4_LONGEST_PREFIX = 30;
    int IPV6_LONGEST_PREFIX = 64;

    /** Detail restricting the BGP peers handed to the guest to these peer ids (comma separated). */
    String DETAIL_BGP_PEERS = "bgp.peers";

    String getName();

    String getDisplayText();

    Integer getIp4PrefixLength();

    Integer getIp6PrefixLength();

    Mode getMode();

    State getState();

    Long getZoneId();

    Long getDomainId();

    Date getCreated();
}
