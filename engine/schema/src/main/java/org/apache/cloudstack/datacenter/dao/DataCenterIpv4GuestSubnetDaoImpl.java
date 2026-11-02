// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.cloudstack.datacenter.dao;

import java.util.List;

import org.apache.cloudstack.datacenter.DataCenterIpv4GuestSubnetVO;
import org.apache.cloudstack.network.routedsubnet.SubnetPoolPurpose;
import org.springframework.stereotype.Component;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
@DB
public class DataCenterIpv4GuestSubnetDaoImpl extends GenericDaoBase<DataCenterIpv4GuestSubnetVO, Long> implements DataCenterIpv4GuestSubnetDao {

    public DataCenterIpv4GuestSubnetDaoImpl() {
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByDataCenterId(long dcId) {
        QueryBuilder<DataCenterIpv4GuestSubnetVO> sc = QueryBuilder.create(DataCenterIpv4GuestSubnetVO.class);
        sc.and(sc.entity().getDataCenterId(), SearchCriteria.Op.EQ, dcId);
        return sc.list();
    }

    /** The dedication lookups without a purpose serve Routed-mode networks, the pools' original use. */
    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByDataCenterIdAndAccountId(long dcId, long accountId) {
        return listByDataCenterIdAndAccountId(dcId, accountId, SubnetPoolPurpose.Network);
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByDataCenterIdAndDomainId(long dcId, long domainId) {
        return listByDataCenterIdAndDomainId(dcId, domainId, SubnetPoolPurpose.Network);
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listNonDedicatedByDataCenterId(long dcId) {
        return listNonDedicatedByDataCenterId(dcId, SubnetPoolPurpose.Network);
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByDataCenterIdAndPurpose(long dcId, SubnetPoolPurpose purpose) {
        QueryBuilder<DataCenterIpv4GuestSubnetVO> sc = QueryBuilder.create(DataCenterIpv4GuestSubnetVO.class);
        sc.and(sc.entity().getDataCenterId(), SearchCriteria.Op.EQ, dcId);
        sc.and(sc.entity().getPurpose(), SearchCriteria.Op.EQ, purpose);
        return sc.list();
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByDataCenterIdAndAccountId(long dcId, long accountId, SubnetPoolPurpose purpose) {
        QueryBuilder<DataCenterIpv4GuestSubnetVO> sc = QueryBuilder.create(DataCenterIpv4GuestSubnetVO.class);
        sc.and(sc.entity().getDataCenterId(), SearchCriteria.Op.EQ, dcId);
        sc.and(sc.entity().getAccountId(), SearchCriteria.Op.EQ, accountId);
        sc.and(sc.entity().getPurpose(), SearchCriteria.Op.EQ, purpose);
        return sc.list();
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByDataCenterIdAndDomainId(long dcId, long domainId, SubnetPoolPurpose purpose) {
        QueryBuilder<DataCenterIpv4GuestSubnetVO> sc = QueryBuilder.create(DataCenterIpv4GuestSubnetVO.class);
        sc.and(sc.entity().getDataCenterId(), SearchCriteria.Op.EQ, dcId);
        sc.and(sc.entity().getDomainId(), SearchCriteria.Op.EQ, domainId);
        sc.and(sc.entity().getAccountId(), SearchCriteria.Op.NULL);
        sc.and(sc.entity().getPurpose(), SearchCriteria.Op.EQ, purpose);
        return sc.list();
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listNonDedicatedByDataCenterId(long dcId, SubnetPoolPurpose purpose) {
        QueryBuilder<DataCenterIpv4GuestSubnetVO> sc = QueryBuilder.create(DataCenterIpv4GuestSubnetVO.class);
        sc.and(sc.entity().getDataCenterId(), SearchCriteria.Op.EQ, dcId);
        sc.and(sc.entity().getDomainId(), SearchCriteria.Op.NULL);
        sc.and(sc.entity().getAccountId(), SearchCriteria.Op.NULL);
        sc.and(sc.entity().getPurpose(), SearchCriteria.Op.EQ, purpose);
        return sc.list();
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByAccountId(long accountId) {
        QueryBuilder<DataCenterIpv4GuestSubnetVO> sc = QueryBuilder.create(DataCenterIpv4GuestSubnetVO.class);
        sc.and(sc.entity().getAccountId(), SearchCriteria.Op.EQ, accountId);
        return sc.list();
    }

    @Override
    public List<DataCenterIpv4GuestSubnetVO> listByDomainId(long domainId) {
        QueryBuilder<DataCenterIpv4GuestSubnetVO> sc = QueryBuilder.create(DataCenterIpv4GuestSubnetVO.class);
        sc.and(sc.entity().getDomainId(), SearchCriteria.Op.EQ, domainId);
        return sc.list();
    }
}
