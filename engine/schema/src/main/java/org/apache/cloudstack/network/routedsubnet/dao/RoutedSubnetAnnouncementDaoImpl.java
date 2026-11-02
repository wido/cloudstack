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
package org.apache.cloudstack.network.routedsubnet.dao;

import java.util.List;

import org.apache.cloudstack.network.routedsubnet.RoutedSubnetAnnouncementVO;
import org.springframework.stereotype.Component;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
@DB
public class RoutedSubnetAnnouncementDaoImpl extends GenericDaoBase<RoutedSubnetAnnouncementVO, Long> implements RoutedSubnetAnnouncementDao {

    private final SearchBuilder<RoutedSubnetAnnouncementVO> SubnetSearch;
    private final SearchBuilder<RoutedSubnetAnnouncementVO> SubnetEndpointSearch;
    private final SearchBuilder<RoutedSubnetAnnouncementVO> PendingSearch;
    private final SearchBuilder<RoutedSubnetAnnouncementVO> EndpointSearch;

    public RoutedSubnetAnnouncementDaoImpl() {
        SubnetSearch = createSearchBuilder();
        SubnetSearch.and("family", SubnetSearch.entity().getAddressFamily(), SearchCriteria.Op.EQ);
        SubnetSearch.and("mapId", SubnetSearch.entity().getSubnetMapId(), SearchCriteria.Op.EQ);
        SubnetSearch.done();

        SubnetEndpointSearch = createSearchBuilder();
        SubnetEndpointSearch.and("family", SubnetEndpointSearch.entity().getAddressFamily(), SearchCriteria.Op.EQ);
        SubnetEndpointSearch.and("mapId", SubnetEndpointSearch.entity().getSubnetMapId(), SearchCriteria.Op.EQ);
        SubnetEndpointSearch.and("endpoint", SubnetEndpointSearch.entity().getEndpoint(), SearchCriteria.Op.EQ);
        SubnetEndpointSearch.done();

        PendingSearch = createSearchBuilder();
        PendingSearch.and("announced", PendingSearch.entity().getAnnounced(), SearchCriteria.Op.NULL);
        PendingSearch.done();

        EndpointSearch = createSearchBuilder();
        EndpointSearch.and("endpoint", EndpointSearch.entity().getEndpoint(), SearchCriteria.Op.EQ);
        EndpointSearch.done();
    }

    @Override
    public List<RoutedSubnetAnnouncementVO> listBySubnet(int addressFamily, long subnetMapId) {
        SearchCriteria<RoutedSubnetAnnouncementVO> sc = SubnetSearch.create();
        sc.setParameters("family", addressFamily);
        sc.setParameters("mapId", subnetMapId);
        return listBy(sc);
    }

    @Override
    public RoutedSubnetAnnouncementVO findBySubnetAndEndpoint(int addressFamily, long subnetMapId, String endpoint) {
        SearchCriteria<RoutedSubnetAnnouncementVO> sc = SubnetEndpointSearch.create();
        sc.setParameters("family", addressFamily);
        sc.setParameters("mapId", subnetMapId);
        sc.setParameters("endpoint", endpoint);
        return findOneBy(sc);
    }

    @Override
    public List<RoutedSubnetAnnouncementVO> listPending() {
        return listBy(PendingSearch.create());
    }

    @Override
    public List<RoutedSubnetAnnouncementVO> listByEndpoint(String endpoint) {
        SearchCriteria<RoutedSubnetAnnouncementVO> sc = EndpointSearch.create();
        sc.setParameters("endpoint", endpoint);
        return listBy(sc);
    }

    @Override
    public int removeBySubnet(int addressFamily, long subnetMapId) {
        SearchCriteria<RoutedSubnetAnnouncementVO> sc = SubnetSearch.create();
        sc.setParameters("family", addressFamily);
        sc.setParameters("mapId", subnetMapId);
        return remove(sc);
    }
}
