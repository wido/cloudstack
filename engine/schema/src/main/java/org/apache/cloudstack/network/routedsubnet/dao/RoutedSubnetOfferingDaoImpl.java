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

import org.apache.cloudstack.network.routedsubnet.RoutedSubnetOffering;
import org.apache.cloudstack.network.routedsubnet.RoutedSubnetOfferingVO;
import org.springframework.stereotype.Component;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
@DB
public class RoutedSubnetOfferingDaoImpl extends GenericDaoBase<RoutedSubnetOfferingVO, Long> implements RoutedSubnetOfferingDao {

    private final SearchBuilder<RoutedSubnetOfferingVO> NameSearch;
    private final SearchBuilder<RoutedSubnetOfferingVO> StateSearch;

    public RoutedSubnetOfferingDaoImpl() {
        NameSearch = createSearchBuilder();
        NameSearch.and("name", NameSearch.entity().getName(), SearchCriteria.Op.EQ);
        NameSearch.done();

        StateSearch = createSearchBuilder();
        StateSearch.and("state", StateSearch.entity().getState(), SearchCriteria.Op.EQ);
        StateSearch.done();
    }

    @Override
    public RoutedSubnetOfferingVO findByName(String name) {
        SearchCriteria<RoutedSubnetOfferingVO> sc = NameSearch.create();
        sc.setParameters("name", name);
        return findOneBy(sc);
    }

    @Override
    public List<RoutedSubnetOfferingVO> listByState(RoutedSubnetOffering.State state) {
        SearchCriteria<RoutedSubnetOfferingVO> sc = StateSearch.create();
        sc.setParameters("state", state);
        return listBy(sc);
    }
}
