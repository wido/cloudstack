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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.cloudstack.api.command.user.routedsubnet.AssignRoutedSubnetCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ReleaseRoutedSubnetCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.datacenter.DataCenterIpv4GuestSubnetVO;
import org.apache.cloudstack.datacenter.dao.DataCenterIpv4GuestSubnetDao;
import org.apache.cloudstack.network.Ipv4GuestSubnetNetworkMap;
import org.apache.cloudstack.network.Ipv4GuestSubnetNetworkMapVO;
import org.apache.cloudstack.network.dao.Ipv4GuestSubnetNetworkMapDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetAnnouncementDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetOfferingDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetOfferingDetailsDao;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.configuration.Resource.ResourceType;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterGuestIpv6PrefixDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.Network;
import com.cloud.network.dao.Ipv6GuestPrefixSubnetNetworkMapDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.ResourceLimitService;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;

@RunWith(MockitoJUnitRunner.Silent.class)
public class RoutedSubnetManagerImplTest {

    @Spy
    @InjectMocks
    RoutedSubnetManagerImpl manager = new RoutedSubnetManagerImpl();

    @Mock
    RoutedSubnetOfferingDao offeringDao;
    @Mock
    RoutedSubnetOfferingDetailsDao offeringDetailsDao;
    @Mock
    RoutedSubnetAnnouncementDao announcementDao;
    @Mock
    Ipv4GuestSubnetNetworkMapDao ipv4MapDao;
    @Mock
    DataCenterIpv4GuestSubnetDao ipv4PoolDao;
    @Mock
    Ipv6GuestPrefixSubnetNetworkMapDao ipv6MapDao;
    @Mock
    DataCenterGuestIpv6PrefixDao ipv6PoolDao;
    @Mock
    NicDao nicDao;
    @Mock
    VMInstanceDao vmInstanceDao;
    @Mock
    NetworkDao networkDao;
    @Mock
    DataCenterDao zoneDao;
    @Mock
    DomainDao domainDao;
    @Mock
    AccountDao accountDao;
    @Mock
    ProjectDao projectDao;
    @Mock
    AccountManager accountManager;
    @Mock
    ResourceLimitService resourceLimitService;
    @Mock
    RouteAnnouncer announcer;

    private static final long ZONE_ID = 1L;
    private static final long ACCOUNT_ID = 2L;
    private static final long DOMAIN_ID = 3L;
    private static final long NIC_ID = 10L;
    private static final long VM_ID = 20L;
    private static final long NETWORK_ID = 30L;
    private static final long OFFERING_ID = 40L;
    private static final long POOL_ID = 50L;

    private AccountVO owner;
    private NicVO nic;
    private VMInstanceVO vm;
    private NetworkVO network;
    private RoutedSubnetOfferingVO offering;
    private DataCenterIpv4GuestSubnetVO pool;

    @Before
    public void setUp() {
        owner = Mockito.mock(AccountVO.class);
        when(owner.getId()).thenReturn(ACCOUNT_ID);
        when(owner.getDomainId()).thenReturn(DOMAIN_ID);
        User user = Mockito.mock(UserVO.class);
        CallContext.register(user, owner);

        nic = Mockito.mock(NicVO.class);
        when(nic.getId()).thenReturn(NIC_ID);
        when(nic.getInstanceId()).thenReturn(VM_ID);
        when(nic.getNetworkId()).thenReturn(NETWORK_ID);
        when(nic.getIPv4Address()).thenReturn("192.0.2.10");
        when(nicDao.findById(NIC_ID)).thenReturn(nic);

        vm = Mockito.mock(VMInstanceVO.class);
        when(vm.getType()).thenReturn(VirtualMachine.Type.User);
        when(vm.getAccountId()).thenReturn(ACCOUNT_ID);
        when(vmInstanceDao.findById(VM_ID)).thenReturn(vm);

        network = Mockito.mock(NetworkVO.class);
        when(network.getGuestType()).thenReturn(Network.GuestType.L3);
        when(network.getDataCenterId()).thenReturn(ZONE_ID);
        when(networkDao.findById(NETWORK_ID)).thenReturn(network);

        when(accountManager.getAccount(ACCOUNT_ID)).thenReturn(owner);

        offering = new RoutedSubnetOfferingVO("v4", "v4", 29, null, RoutedSubnetOffering.Mode.BGP, null, null);
        when(offeringDao.findById(OFFERING_ID)).thenReturn(offering);

        pool = new DataCenterIpv4GuestSubnetVO(ZONE_ID, "10.0.0.0/24", SubnetPoolPurpose.Instance);
        when(ipv4PoolDao.listNonDedicatedByDataCenterId(ZONE_ID, SubnetPoolPurpose.Instance)).thenReturn(Collections.singletonList(pool));
        when(ipv4MapDao.persist(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @After
    public void tearDown() {
        CallContext.unregister();
    }

    private AssignRoutedSubnetCmd assignCmd() {
        AssignRoutedSubnetCmd cmd = Mockito.mock(AssignRoutedSubnetCmd.class);
        when(cmd.getNicId()).thenReturn(NIC_ID);
        when(cmd.getOfferingId()).thenReturn(OFFERING_ID);
        when(cmd.getSubnetId()).thenReturn(null);
        return cmd;
    }

    @Test
    public void assignCarvesTheFirstFreeBlockWhenNoFreeRowExists() throws Exception {
        Ipv4GuestSubnetNetworkMapVO taken = new Ipv4GuestSubnetNetworkMapVO(POOL_ID, ZONE_ID, "10.0.0.0/29", null, Ipv4GuestSubnetNetworkMap.State.Allocated);
        when(ipv4MapDao.listByParent(anyLong())).thenReturn(Collections.singletonList(taken));

        List<RoutedSubnet> result = manager.assignRoutedSubnet(assignCmd());

        Assert.assertEquals(1, result.size());
        ArgumentCaptor<Ipv4GuestSubnetNetworkMapVO> captor = ArgumentCaptor.forClass(Ipv4GuestSubnetNetworkMapVO.class);
        verify(ipv4MapDao).persist(captor.capture());
        Assert.assertEquals("10.0.0.8/29", captor.getValue().getSubnet());
        verify(resourceLimitService).checkResourceLimit(owner, ResourceType.routed_subnet, 1L);
        verify(resourceLimitService).incrementResourceCount(ACCOUNT_ID, ResourceType.routed_subnet, 1L);
        verify(announcer, never()).announce(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    public void assignReusesAFreeRowOfTheRightSizeFirst() throws ResourceAllocationException {
        Ipv4GuestSubnetNetworkMapVO free = new Ipv4GuestSubnetNetworkMapVO(POOL_ID, ZONE_ID, "10.0.0.64/29", null, Ipv4GuestSubnetNetworkMap.State.Free);
        when(ipv4MapDao.findFirstAvailable(anyLong(), eq(29L))).thenReturn(free);

        manager.assignRoutedSubnet(assignCmd());

        verify(ipv4MapDao, never()).persist(any());
        Assert.assertEquals(Ipv4GuestSubnetNetworkMap.State.Allocated, free.getState());
        Assert.assertEquals(Long.valueOf(NIC_ID), free.getNicId());
        Assert.assertEquals(Long.valueOf(ACCOUNT_ID), free.getAccountId());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void assignRejectsAnIsolatedNetwork() throws ResourceAllocationException {
        when(network.getGuestType()).thenReturn(Network.GuestType.Isolated);
        manager.assignRoutedSubnet(assignCmd());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void assignRejectsAnOfferingWhoseFamilyTheNicLacks() throws ResourceAllocationException {
        when(nic.getIPv4Address()).thenReturn(null);
        manager.assignRoutedSubnet(assignCmd());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void staticModeNeedsAnAnnouncerInTheZone() throws ResourceAllocationException {
        RoutedSubnetOfferingVO staticOffering = new RoutedSubnetOfferingVO("s", "s", 29, null, RoutedSubnetOffering.Mode.Static, null, null);
        when(offeringDao.findById(OFFERING_ID)).thenReturn(staticOffering);
        doReturn(null).when(manager).getAnnouncer(ZONE_ID);
        manager.assignRoutedSubnet(assignCmd());
    }

    @Test
    public void staticModeAnnouncesToEveryEndpointAndRecordsFailures() throws Exception {
        RoutedSubnetOfferingVO staticOffering = new RoutedSubnetOfferingVO("s", "s", 29, null, RoutedSubnetOffering.Mode.Static, null, null);
        when(offeringDao.findById(OFFERING_ID)).thenReturn(staticOffering);
        doReturn(announcer).when(manager).getAnnouncer(ZONE_ID);
        when(announcer.getEndpoints(ZONE_ID)).thenReturn(Arrays.asList("ep1", "ep2"));
        when(announcementDao.persist(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RouteAnnouncementException("down")).when(announcer).announce(ZONE_ID, "ep2", "10.0.0.0/29", "192.0.2.10");
        when(ipv4MapDao.listByParent(anyLong())).thenReturn(new ArrayList<>());

        manager.assignRoutedSubnet(assignCmd());

        verify(announcer).announce(ZONE_ID, "ep1", "10.0.0.0/29", "192.0.2.10");
        verify(announcer).announce(ZONE_ID, "ep2", "10.0.0.0/29", "192.0.2.10");
        ArgumentCaptor<RoutedSubnetAnnouncementVO> captor = ArgumentCaptor.forClass(RoutedSubnetAnnouncementVO.class);
        verify(announcementDao, Mockito.times(2)).update(anyLong(), captor.capture());
        List<RoutedSubnetAnnouncementVO> records = captor.getAllValues();
        Assert.assertNotNull(records.get(0).getAnnounced());
        Assert.assertNull(records.get(1).getAnnounced());
        Assert.assertEquals("down", records.get(1).getLastError());
    }

    @Test
    public void anycastAttachNeedsBgpModeAndTheSameAccount() throws ResourceAllocationException {
        Ipv4GuestSubnetNetworkMapVO existing = new Ipv4GuestSubnetNetworkMapVO(POOL_ID, ZONE_ID, "10.0.0.0/29", null, Ipv4GuestSubnetNetworkMap.State.Allocated);
        existing.setOfferingId(OFFERING_ID);
        existing.setAccountId(ACCOUNT_ID);
        existing.setNicId(99L);
        when(ipv4MapDao.findByUuid("subnet-uuid")).thenReturn(existing);
        when(ipv4MapDao.listBySubnet("10.0.0.0/29")).thenReturn(Collections.singletonList(existing));
        AssignRoutedSubnetCmd cmd = Mockito.mock(AssignRoutedSubnetCmd.class);
        when(cmd.getNicId()).thenReturn(NIC_ID);
        when(cmd.getOfferingId()).thenReturn(null);
        when(cmd.getSubnetId()).thenReturn("subnet-uuid");

        List<RoutedSubnet> result = manager.assignRoutedSubnet(cmd);
        Assert.assertEquals(1, result.size());
        ArgumentCaptor<Ipv4GuestSubnetNetworkMapVO> captor = ArgumentCaptor.forClass(Ipv4GuestSubnetNetworkMapVO.class);
        verify(ipv4MapDao).persist(captor.capture());
        Assert.assertEquals("10.0.0.0/29", captor.getValue().getSubnet());
        Assert.assertEquals(Long.valueOf(NIC_ID), captor.getValue().getNicId());
        verify(resourceLimitService, never()).incrementResourceCount(anyLong(), any(), any());

        existing.setAccountId(ACCOUNT_ID + 1);
        try {
            manager.assignRoutedSubnet(cmd);
            Assert.fail("another account's subnet must be rejected");
        } catch (InvalidParameterValueException expected) {
        }
    }

    @Test
    public void releaseKeepsTheRowReleasingWhileAnEndpointStillHoldsIt() throws Exception {
        RoutedSubnetOfferingVO staticOffering = new RoutedSubnetOfferingVO("s", "s", 29, null, RoutedSubnetOffering.Mode.Static, null, null);
        when(offeringDao.findById(OFFERING_ID)).thenReturn(staticOffering);
        Ipv4GuestSubnetNetworkMapVO row = new Ipv4GuestSubnetNetworkMapVO(POOL_ID, ZONE_ID, "10.0.0.0/29", null, Ipv4GuestSubnetNetworkMap.State.Allocated);
        row.setOfferingId(OFFERING_ID);
        row.setAccountId(ACCOUNT_ID);
        row.setNicId(NIC_ID);
        when(ipv4MapDao.findByUuid("row")).thenReturn(row);
        when(ipv4MapDao.listBySubnet("10.0.0.0/29")).thenReturn(Collections.singletonList(row));
        when(nicDao.findByIdIncludingRemoved(NIC_ID)).thenReturn(nic);
        doReturn(announcer).when(manager).getAnnouncer(ZONE_ID);
        RoutedSubnetAnnouncementVO held = new RoutedSubnetAnnouncementVO(4, row.getId(), "ep1");
        held.setAnnounced(new Date());
        when(announcementDao.listBySubnet(4, row.getId())).thenReturn(Collections.singletonList(held));
        doThrow(new RouteAnnouncementException("down")).when(announcer).withdraw(ZONE_ID, "ep1", "10.0.0.0/29", "192.0.2.10");
        ReleaseRoutedSubnetCmd cmd = Mockito.mock(ReleaseRoutedSubnetCmd.class);
        when(cmd.getId()).thenReturn("row");
        when(cmd.getNicId()).thenReturn(null);

        manager.releaseRoutedSubnet(cmd);

        Assert.assertEquals(Ipv4GuestSubnetNetworkMap.State.Releasing, row.getState());
        Assert.assertEquals(Long.valueOf(NIC_ID), row.getNicId());
        verify(resourceLimitService).decrementResourceCount(ACCOUNT_ID, ResourceType.routed_subnet, 1L);
        verify(announcementDao, never()).remove(anyLong());
    }

    @Test
    public void releaseFreesTheRowWhenNothingHoldsIt() {
        Ipv4GuestSubnetNetworkMapVO row = new Ipv4GuestSubnetNetworkMapVO(POOL_ID, ZONE_ID, "10.0.0.0/29", null, Ipv4GuestSubnetNetworkMap.State.Allocated);
        row.setOfferingId(OFFERING_ID);
        row.setAccountId(ACCOUNT_ID);
        row.setNicId(NIC_ID);
        when(ipv4MapDao.findByUuid("row")).thenReturn(row);
        when(ipv4MapDao.listBySubnet("10.0.0.0/29")).thenReturn(Collections.singletonList(row));
        ReleaseRoutedSubnetCmd cmd = Mockito.mock(ReleaseRoutedSubnetCmd.class);
        when(cmd.getId()).thenReturn("row");
        when(cmd.getNicId()).thenReturn(null);

        manager.releaseRoutedSubnet(cmd);

        Assert.assertEquals(Ipv4GuestSubnetNetworkMap.State.Free, row.getState());
        Assert.assertNull(row.getNicId());
        Assert.assertNull(row.getOfferingId());
        Assert.assertNull(row.getAccountId());
    }
}
