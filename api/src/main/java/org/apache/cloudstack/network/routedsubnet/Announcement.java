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

import java.util.Objects;

/** One route an announcer endpoint holds: a prefix and the next hop it is routed to. */
public final class Announcement {

    private final String prefix;
    private final String nextHop;

    public Announcement(String prefix, String nextHop) {
        this.prefix = prefix;
        this.nextHop = nextHop;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNextHop() {
        return nextHop;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Announcement)) {
            return false;
        }
        Announcement other = (Announcement) o;
        return Objects.equals(prefix, other.prefix) && Objects.equals(nextHop, other.nextHop);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, nextHop);
    }

    @Override
    public String toString() {
        return prefix + " via " + nextHop;
    }
}
