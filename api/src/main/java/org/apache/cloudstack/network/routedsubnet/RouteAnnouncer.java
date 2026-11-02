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

import java.util.List;
import java.util.Set;

import com.cloud.utils.component.Adapter;

/**
 * Gets a routed subnet's route into the fabric: "prefix via the Instance's address". One
 * announcer is selected per zone by the routed.subnet.announcer setting, and it may know
 * several endpoints (speakers) for the zone. The management server keeps, per subnet and
 * endpoint, what the endpoint has confirmed; the announcer itself holds no state the database
 * does not have, and the reconciler makes every endpoint match the database.
 *
 * All calls are idempotent: announcing a route an endpoint holds, or withdrawing one it does
 * not, succeeds.
 */
public interface RouteAnnouncer extends Adapter {

    /** The endpoints configured for the zone, in the order the failover strategy tries them. */
    List<String> getEndpoints(long zoneId);

    void announce(long zoneId, String endpoint, String prefix, String nextHop) throws RouteAnnouncementException;

    void withdraw(long zoneId, String endpoint, String prefix, String nextHop) throws RouteAnnouncementException;

    /** Everything the endpoint holds, for reconciliation. */
    Set<Announcement> list(long zoneId, String endpoint) throws RouteAnnouncementException;

    /**
     * An identifier the endpoint picks when it starts. A change means the speaker restarted and
     * its routes must be replayed; an exception means it is unreachable.
     */
    String getInstanceId(long zoneId, String endpoint) throws RouteAnnouncementException;
}
