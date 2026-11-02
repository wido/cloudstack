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

import com.cloud.utils.db.GenericDao;

public interface RoutedSubnetAnnouncementDao extends GenericDao<RoutedSubnetAnnouncementVO, Long> {

    List<RoutedSubnetAnnouncementVO> listBySubnet(int addressFamily, long subnetMapId);

    RoutedSubnetAnnouncementVO findBySubnetAndEndpoint(int addressFamily, long subnetMapId, String endpoint);

    /** Rows no endpoint has confirmed yet, for the reconciler. */
    List<RoutedSubnetAnnouncementVO> listPending();

    List<RoutedSubnetAnnouncementVO> listByEndpoint(String endpoint);

    int removeBySubnet(int addressFamily, long subnetMapId);
}
