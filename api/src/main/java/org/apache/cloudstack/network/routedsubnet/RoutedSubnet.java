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

import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

/**
 * A subnet routed to the unicast address of an Instance's NIC. Backed by a child row of the
 * IPv4 subnet or IPv6 prefix pools; the address family says which. Several rows may carry the
 * same subnet when it is anycast in BGP mode.
 */
public interface RoutedSubnet extends ControlledEntity, Identity, InternalIdentity {

    int getAddressFamily();

    String getSubnet();

    Long getNicId();

    Long getOfferingId();

    /** Allocated, Releasing or Free, as the pool child row states it. */
    String getState();

    Long getParentId();

    Date getCreated();

    Date getAllocated();
}
