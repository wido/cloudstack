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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.cloud.utils.db.GenericDao;

@Entity
@Table(name = "routed_subnet_announcements")
public class RoutedSubnetAnnouncementVO implements RoutedSubnetAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "address_family")
    private int addressFamily;

    @Column(name = "subnet_map_id")
    private long subnetMapId;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "announced")
    @Temporal(TemporalType.TIMESTAMP)
    private Date announced;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "last_attempt")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastAttempt;

    @Column(name = GenericDao.CREATED_COLUMN)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    @Temporal(TemporalType.TIMESTAMP)
    private Date removed;

    protected RoutedSubnetAnnouncementVO() {
    }

    public RoutedSubnetAnnouncementVO(int addressFamily, long subnetMapId, String endpoint) {
        this.addressFamily = addressFamily;
        this.subnetMapId = subnetMapId;
        this.endpoint = endpoint;
        this.created = new Date();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public int getAddressFamily() {
        return addressFamily;
    }

    @Override
    public long getSubnetMapId() {
        return subnetMapId;
    }

    @Override
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public Date getAnnounced() {
        return announced;
    }

    public void setAnnounced(Date announced) {
        this.announced = announced;
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null ? null : (lastError.length() > 1024 ? lastError.substring(0, 1024) : lastError);
    }

    @Override
    public Date getLastAttempt() {
        return lastAttempt;
    }

    public void setLastAttempt(Date lastAttempt) {
        this.lastAttempt = lastAttempt;
    }

    public Date getCreated() {
        return created;
    }
}
