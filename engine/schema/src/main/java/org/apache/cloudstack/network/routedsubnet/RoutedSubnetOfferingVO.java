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
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.cloud.utils.db.GenericDao;

@Entity
@Table(name = "routed_subnet_offerings")
public class RoutedSubnetOfferingVO implements RoutedSubnetOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "name")
    private String name;

    @Column(name = "display_text")
    private String displayText;

    @Column(name = "ip4_prefix_length")
    private Integer ip4PrefixLength;

    @Column(name = "ip6_prefix_length")
    private Integer ip6PrefixLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private State state = State.Active;

    @Column(name = "data_center_id")
    private Long zoneId;

    @Column(name = "domain_id")
    private Long domainId;

    @Column(name = GenericDao.CREATED_COLUMN)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    @Temporal(TemporalType.TIMESTAMP)
    private Date removed;

    protected RoutedSubnetOfferingVO() {
        uuid = UUID.randomUUID().toString();
    }

    public RoutedSubnetOfferingVO(String name, String displayText, Integer ip4PrefixLength, Integer ip6PrefixLength, Mode mode, Long zoneId, Long domainId) {
        this();
        this.name = name;
        this.displayText = displayText;
        this.ip4PrefixLength = ip4PrefixLength;
        this.ip6PrefixLength = ip6PrefixLength;
        this.mode = mode;
        this.zoneId = zoneId;
        this.domainId = domainId;
        this.created = new Date();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    @Override
    public Integer getIp4PrefixLength() {
        return ip4PrefixLength;
    }

    @Override
    public Integer getIp6PrefixLength() {
        return ip6PrefixLength;
    }

    @Override
    public Mode getMode() {
        return mode;
    }

    @Override
    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    @Override
    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public Long getDomainId() {
        return domainId;
    }

    public void setDomainId(Long domainId) {
        this.domainId = domainId;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    public Date getRemoved() {
        return removed;
    }

    @Override
    public String toString() {
        return String.format("RoutedSubnetOffering %s", org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "id", "uuid", "name", "mode"));
    }
}
