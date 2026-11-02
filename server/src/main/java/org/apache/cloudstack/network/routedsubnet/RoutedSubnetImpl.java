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

import org.apache.cloudstack.network.Ipv4GuestSubnetNetworkMapVO;

import com.cloud.network.Ipv6GuestPrefixSubnetNetworkMapVO;

/** The {@link RoutedSubnet} view over a child row of either pool table. */
public class RoutedSubnetImpl implements RoutedSubnet {

    private final Ipv4GuestSubnetNetworkMapVO v4;
    private final Ipv6GuestPrefixSubnetNetworkMapVO v6;

    public RoutedSubnetImpl(Ipv4GuestSubnetNetworkMapVO v4) {
        this.v4 = v4;
        this.v6 = null;
    }

    public RoutedSubnetImpl(Ipv6GuestPrefixSubnetNetworkMapVO v6) {
        this.v4 = null;
        this.v6 = v6;
    }

    public Ipv4GuestSubnetNetworkMapVO getV4() {
        return v4;
    }

    public Ipv6GuestPrefixSubnetNetworkMapVO getV6() {
        return v6;
    }

    @Override
    public int getAddressFamily() {
        return v4 != null ? 4 : 6;
    }

    @Override
    public long getId() {
        return v4 != null ? v4.getId() : v6.getId();
    }

    @Override
    public String getUuid() {
        return v4 != null ? v4.getUuid() : v6.getUuid();
    }

    @Override
    public String getSubnet() {
        return v4 != null ? v4.getSubnet() : v6.getSubnet();
    }

    @Override
    public Long getNicId() {
        return v4 != null ? v4.getNicId() : v6.getNicId();
    }

    @Override
    public Long getOfferingId() {
        return v4 != null ? v4.getOfferingId() : v6.getOfferingId();
    }

    @Override
    public String getState() {
        return v4 != null ? v4.getState().name() : v6.getState().name();
    }

    @Override
    public Long getParentId() {
        return v4 != null ? v4.getParentId() : v6.getPrefixId();
    }

    @Override
    public Date getCreated() {
        return v4 != null ? v4.getCreated() : v6.getCreated();
    }

    @Override
    public Date getAllocated() {
        return v4 != null ? v4.getAllocated() : v6.getUpdated();
    }

    @Override
    public long getAccountId() {
        Long id = v4 != null ? v4.getAccountId() : v6.getAccountId();
        return id == null ? -1 : id;
    }

    @Override
    public long getDomainId() {
        Long id = v4 != null ? v4.getDomainId() : v6.getDomainId();
        return id == null ? -1 : id;
    }

    @Override
    public Class<?> getEntityType() {
        return RoutedSubnet.class;
    }

    @Override
    public String getName() {
        return getSubnet();
    }

    @Override
    public String toString() {
        return String.format("RoutedSubnet [%s, uuid %s, nic %s]", getSubnet(), getUuid(), getNicId());
    }
}
