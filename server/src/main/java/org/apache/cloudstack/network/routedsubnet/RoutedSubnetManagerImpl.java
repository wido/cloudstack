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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.cloudstack.acl.SecurityChecker.AccessType;
import org.apache.cloudstack.api.command.admin.routedsubnet.CreateRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.DeleteRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.ReannounceRoutedSubnetCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.UpdateRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.AssignRoutedSubnetCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ListRoutedSubnetOfferingsCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ListRoutedSubnetsCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ReleaseRoutedSubnetCmd;
import org.apache.cloudstack.api.response.RoutedSubnetEndpointResponse;
import org.apache.cloudstack.api.response.RoutedSubnetNicResponse;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.api.response.RoutedSubnetResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.datacenter.DataCenterIpv4GuestSubnetVO;
import org.apache.cloudstack.datacenter.dao.DataCenterIpv4GuestSubnetDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.network.Ipv4GuestSubnetNetworkMap;
import org.apache.cloudstack.network.Ipv4GuestSubnetNetworkMapVO;
import org.apache.cloudstack.network.dao.Ipv4GuestSubnetNetworkMapDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetAnnouncementDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetOfferingDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetOfferingDetailsDao;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.cloud.configuration.Resource.ResourceType;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterGuestIpv6PrefixVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterGuestIpv6PrefixDao;
import com.cloud.domain.Domain;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.Ipv6GuestPrefixSubnetNetworkMap;
import com.cloud.network.Ipv6GuestPrefixSubnetNetworkMapVO;
import com.cloud.network.Network;
import com.cloud.network.dao.Ipv6GuestPrefixSubnetNetworkMapDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.projects.Project;
import com.cloud.projects.Project.ListProjectResourcesCriteria;
import com.cloud.projects.dao.ProjectDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.ResourceLimitService;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.ModifyRoutedSubnetCommand;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.network.NetworkModel;
import com.cloud.network.security.SecurityGroupManager;
import com.cloud.utils.net.NetUtils;

public class RoutedSubnetManagerImpl extends ManagerBase implements RoutedSubnetService {

    @Inject
    private RoutedSubnetOfferingDao offeringDao;
    @Inject
    private RoutedSubnetOfferingDetailsDao offeringDetailsDao;
    @Inject
    private RoutedSubnetAnnouncementDao announcementDao;
    @Inject
    private Ipv4GuestSubnetNetworkMapDao ipv4MapDao;
    @Inject
    private DataCenterIpv4GuestSubnetDao ipv4PoolDao;
    @Inject
    private Ipv6GuestPrefixSubnetNetworkMapDao ipv6MapDao;
    @Inject
    private DataCenterGuestIpv6PrefixDao ipv6PoolDao;
    @Inject
    private NicDao nicDao;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private NetworkDao networkDao;
    @Inject
    private DataCenterDao zoneDao;
    @Inject
    private DomainDao domainDao;
    @Inject
    private AccountDao accountDao;
    @Inject
    private ProjectDao projectDao;
    @Inject
    private AccountManager accountManager;
    @Inject
    private ResourceLimitService resourceLimitService;
    @Inject
    private AgentManager agentManager;
    @Inject
    private NetworkModel networkModel;
    @Inject
    private SecurityGroupManager securityGroupManager;

    private List<RouteAnnouncer> routeAnnouncers = new ArrayList<>();
    private ScheduledExecutorService scheduler;
    /** Last instance id seen per endpoint; null while unreachable. Keyed by zone and endpoint. */
    private final Map<String, String> endpointInstanceIds = new ConcurrentHashMap<>();

    public List<RouteAnnouncer> getRouteAnnouncers() {
        return routeAnnouncers;
    }

    public void setRouteAnnouncers(List<RouteAnnouncer> routeAnnouncers) {
        this.routeAnnouncers = routeAnnouncers == null ? new ArrayList<>() : routeAnnouncers;
    }

    @Override
    public String getConfigComponentName() {
        return RoutedSubnetService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
                RoutedSubnetsEnabled,
                RoutedSubnetIpv4ShortestPrefix,
                RoutedSubnetIpv6ShortestPrefix,
                RoutedSubnetAnnouncer,
                RoutedSubnetAnnouncerStrategy,
                RoutedSubnetAnnouncerHealthInterval,
                RoutedSubnetReconcileInterval
        };
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        cmdList.add(CreateRoutedSubnetOfferingCmd.class);
        cmdList.add(UpdateRoutedSubnetOfferingCmd.class);
        cmdList.add(DeleteRoutedSubnetOfferingCmd.class);
        cmdList.add(ListRoutedSubnetOfferingsCmd.class);
        cmdList.add(AssignRoutedSubnetCmd.class);
        cmdList.add(ReleaseRoutedSubnetCmd.class);
        cmdList.add(ListRoutedSubnetsCmd.class);
        cmdList.add(ReannounceRoutedSubnetCmd.class);
        return cmdList;
    }

    @Override
    public boolean start() {
        scheduler = Executors.newScheduledThreadPool(1, new NamedThreadFactory("RoutedSubnet-Announcer"));
        int reconcile = RoutedSubnetReconcileInterval.value();
        if (reconcile > 0) {
            scheduler.scheduleWithFixedDelay(new ReconcileTask(), 60, reconcile, TimeUnit.SECONDS);
        }
        int health = RoutedSubnetAnnouncerHealthInterval.value();
        if (health > 0) {
            scheduler.scheduleWithFixedDelay(new HealthTask(), 30, health, TimeUnit.SECONDS);
        }
        return true;
    }

    @Override
    public boolean stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        return true;
    }

    // ---------------------------------------------------------------- offerings

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_ROUTED_SUBNET_OFFERING_CREATE, eventDescription = "creating Routed Subnet Offering")
    public RoutedSubnetOffering createRoutedSubnetOffering(CreateRoutedSubnetOfferingCmd cmd) {
        final String name = cmd.getName();
        if (StringUtils.isBlank(name)) {
            throw new InvalidParameterValueException("A name is required");
        }
        if (offeringDao.findByName(name) != null) {
            throw new InvalidParameterValueException(String.format("A Routed Subnet Offering named %s already exists", name));
        }
        final Integer ip4 = cmd.getIp4PrefixLength();
        final Integer ip6 = cmd.getIp6PrefixLength();
        if (ip4 == null && ip6 == null) {
            throw new InvalidParameterValueException("At least one of ip4prefixlength and ip6prefixlength is required");
        }
        final RoutedSubnetOffering.Mode mode;
        try {
            mode = RoutedSubnetOffering.Mode.fromString(cmd.getMode());
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterValueException("mode must be Static or BGP");
        }
        final Long zoneId = cmd.getZoneId();
        if (zoneId != null && zoneDao.findById(zoneId) == null) {
            throw new InvalidParameterValueException("Unable to find zone with id " + zoneId);
        }
        final Long domainId = cmd.getDomainId();
        if (domainId != null && domainDao.findById(domainId) == null) {
            throw new InvalidParameterValueException("Unable to find domain with id " + domainId);
        }
        validatePrefixLengths(ip4, ip6, zoneId);

        final Map<String, String> details = cmd.getDetails();
        return Transaction.execute(new TransactionCallback<RoutedSubnetOffering>() {
            @Override
            public RoutedSubnetOffering doInTransaction(TransactionStatus status) {
                RoutedSubnetOfferingVO offering = new RoutedSubnetOfferingVO(name, cmd.getDisplayText(), ip4, ip6, mode, zoneId, domainId);
                offering = offeringDao.persist(offering);
                for (Map.Entry<String, String> detail : details.entrySet()) {
                    offeringDetailsDao.addDetail(offering.getId(), detail.getKey(), detail.getValue(), true);
                }
                return offering;
            }
        });
    }

    /**
     * Every prefix length an offering hands out sits between the hard floor and the zone's
     * shortest allowed prefix. Checked when the offering is made and again at assignment, since
     * the zone setting may have been tightened in between. A zone-less offering is checked
     * against the global default of the setting.
     */
    protected void validatePrefixLengths(Integer ip4, Integer ip6, Long zoneId) {
        if (ip4 != null) {
            int shortest = zoneId == null ? RoutedSubnetIpv4ShortestPrefix.value() : RoutedSubnetIpv4ShortestPrefix.valueIn(zoneId);
            if (ip4 < shortest || ip4 > RoutedSubnetOffering.IPV4_LONGEST_PREFIX) {
                throw new InvalidParameterValueException(String.format("ip4prefixlength must be between %d and %d", shortest, RoutedSubnetOffering.IPV4_LONGEST_PREFIX));
            }
        }
        if (ip6 != null) {
            int shortest = zoneId == null ? RoutedSubnetIpv6ShortestPrefix.value() : RoutedSubnetIpv6ShortestPrefix.valueIn(zoneId);
            if (ip6 < shortest || ip6 > RoutedSubnetOffering.IPV6_LONGEST_PREFIX) {
                throw new InvalidParameterValueException(String.format("ip6prefixlength must be between %d and %d", shortest, RoutedSubnetOffering.IPV6_LONGEST_PREFIX));
            }
        }
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_ROUTED_SUBNET_OFFERING_UPDATE, eventDescription = "updating Routed Subnet Offering")
    public RoutedSubnetOffering updateRoutedSubnetOffering(UpdateRoutedSubnetOfferingCmd cmd) {
        RoutedSubnetOfferingVO offering = offeringDao.findById(cmd.getId());
        if (offering == null) {
            throw new InvalidParameterValueException("Unable to find Routed Subnet Offering with id " + cmd.getId());
        }
        if (cmd.getName() != null) {
            RoutedSubnetOfferingVO other = offeringDao.findByName(cmd.getName());
            if (other != null && other.getId() != offering.getId()) {
                throw new InvalidParameterValueException(String.format("A Routed Subnet Offering named %s already exists", cmd.getName()));
            }
            offering.setName(cmd.getName());
        }
        if (cmd.getDisplayText() != null) {
            offering.setDisplayText(cmd.getDisplayText());
        }
        if (cmd.getState() != null) {
            try {
                offering.setState(RoutedSubnetOffering.State.valueOf(cmd.getState()));
            } catch (IllegalArgumentException e) {
                throw new InvalidParameterValueException("state must be Active or Inactive");
            }
        }
        if (cmd.getZoneId() != null) {
            if (cmd.getZoneId() == -1L) {
                offering.setZoneId(null);
            } else {
                if (zoneDao.findById(cmd.getZoneId()) == null) {
                    throw new InvalidParameterValueException("Unable to find zone with id " + cmd.getZoneId());
                }
                offering.setZoneId(cmd.getZoneId());
            }
        }
        if (cmd.getDomainId() != null) {
            if (cmd.getDomainId() == -1L) {
                offering.setDomainId(null);
            } else {
                if (domainDao.findById(cmd.getDomainId()) == null) {
                    throw new InvalidParameterValueException("Unable to find domain with id " + cmd.getDomainId());
                }
                offering.setDomainId(cmd.getDomainId());
            }
        }
        offeringDao.update(offering.getId(), offering);
        return offeringDao.findById(offering.getId());
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_ROUTED_SUBNET_OFFERING_DELETE, eventDescription = "deleting Routed Subnet Offering")
    public boolean deleteRoutedSubnetOffering(DeleteRoutedSubnetOfferingCmd cmd) {
        RoutedSubnetOfferingVO offering = offeringDao.findById(cmd.getId());
        if (offering == null) {
            throw new InvalidParameterValueException("Unable to find Routed Subnet Offering with id " + cmd.getId());
        }
        if (CollectionUtils.isNotEmpty(ipv4MapDao.listByOfferingId(offering.getId()))
                || CollectionUtils.isNotEmpty(ipv6MapDao.listByOfferingId(offering.getId()))) {
            throw new InvalidParameterValueException("The Routed Subnet Offering is in use; release its subnets first");
        }
        offeringDetailsDao.removeDetails(offering.getId());
        return offeringDao.remove(offering.getId());
    }

    @Override
    public Pair<List<? extends RoutedSubnetOffering>, Integer> listRoutedSubnetOfferings(ListRoutedSubnetOfferingsCmd cmd) {
        Account caller = CallContext.current().getCallingAccount();
        boolean admin = accountManager.isRootAdmin(caller.getId());

        Filter filter = new Filter(RoutedSubnetOfferingVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        SearchBuilder<RoutedSubnetOfferingVO> sb = offeringDao.createSearchBuilder();
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.EQ);
        sb.and("mode", sb.entity().getMode(), SearchCriteria.Op.EQ);
        sb.and("state", sb.entity().getState(), SearchCriteria.Op.EQ);
        sb.and("keyword", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.done();
        SearchCriteria<RoutedSubnetOfferingVO> sc = sb.create();
        if (cmd.getId() != null) {
            sc.setParameters("id", cmd.getId());
        }
        if (cmd.getName() != null) {
            sc.setParameters("name", cmd.getName());
        }
        if (cmd.getMode() != null) {
            sc.setParameters("mode", RoutedSubnetOffering.Mode.fromString(cmd.getMode()));
        }
        if (cmd.getState() != null && admin) {
            sc.setParameters("state", RoutedSubnetOffering.State.valueOf(cmd.getState()));
        } else if (!admin) {
            sc.setParameters("state", RoutedSubnetOffering.State.Active);
        }
        if (cmd.getKeyword() != null) {
            sc.setParameters("keyword", "%" + cmd.getKeyword() + "%");
        }
        List<RoutedSubnetOfferingVO> all = offeringDao.search(sc, null);

        // Zone and domain visibility are nullable "any" columns, filtered here rather than in SQL.
        Long zoneId = cmd.getZoneId();
        Long domainId = cmd.getDomainId() != null ? cmd.getDomainId() : (admin ? null : caller.getDomainId());
        List<RoutedSubnetOffering> visible = new ArrayList<>();
        for (RoutedSubnetOfferingVO offering : all) {
            if (zoneId != null && offering.getZoneId() != null && !offering.getZoneId().equals(zoneId)) {
                continue;
            }
            if (domainId != null && offering.getDomainId() != null && !isVisibleInDomain(offering.getDomainId(), domainId)) {
                continue;
            }
            visible.add(offering);
        }
        Pair<List<RoutedSubnetOffering>, Integer> paged = page(visible, filter);
        return new Pair<>(paged.first(), paged.second());
    }

    private <T> Pair<List<T>, Integer> page(List<T> all, Filter filter) {
        int total = all.size();
        long start = filter.getOffset() == null ? 0 : filter.getOffset();
        long limit = filter.getLimit() == null ? total : filter.getLimit();
        List<T> page = new ArrayList<>();
        for (long i = start; i < total && i < start + limit; i++) {
            page.add(all.get((int) i));
        }
        return new Pair<>(page, total);
    }

    /** An offering limited to a domain is visible in that domain and its children. */
    protected boolean isVisibleInDomain(long offeringDomainId, long domainId) {
        if (offeringDomainId == domainId) {
            return true;
        }
        return domainDao.isChildDomain(offeringDomainId, domainId);
    }

    @Override
    public RoutedSubnetOfferingResponse createRoutedSubnetOfferingResponse(RoutedSubnetOffering offering) {
        RoutedSubnetOfferingResponse response = new RoutedSubnetOfferingResponse();
        response.setId(offering.getUuid());
        response.setName(offering.getName());
        response.setDisplayText(offering.getDisplayText());
        response.setIp4PrefixLength(offering.getIp4PrefixLength());
        response.setIp6PrefixLength(offering.getIp6PrefixLength());
        response.setMode(offering.getMode().name());
        response.setState(offering.getState().name());
        response.setCreated(offering.getCreated());
        if (offering.getZoneId() != null) {
            DataCenter zone = zoneDao.findById(offering.getZoneId());
            if (zone != null) {
                response.setZoneId(zone.getUuid());
                response.setZoneName(zone.getName());
            }
        }
        if (offering.getDomainId() != null) {
            Domain domain = domainDao.findById(offering.getDomainId());
            if (domain != null) {
                response.setDomainId(domain.getUuid());
                response.setDomainName(domain.getName());
            }
        }
        Map<String, String> details = new HashMap<>();
        for (RoutedSubnetOfferingDetailsVO detail : offeringDetailsDao.listDetails(offering.getId())) {
            details.put(detail.getName(), detail.getValue());
        }
        if (!details.isEmpty()) {
            response.setDetails(details);
        }
        response.setObjectName("routedsubnetoffering");
        return response;
    }

    // ---------------------------------------------------------------- assignment

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_ROUTED_SUBNET_ASSIGN, eventDescription = "routing a subnet to a NIC", async = true)
    public List<RoutedSubnet> assignRoutedSubnet(AssignRoutedSubnetCmd cmd) throws ResourceAllocationException {
        final Account caller = CallContext.current().getCallingAccount();
        final NicVO nic = nicDao.findById(cmd.getNicId());
        if (nic == null) {
            throw new InvalidParameterValueException("Unable to find NIC with id " + cmd.getNicId());
        }
        final VMInstanceVO vm = vmInstanceDao.findById(nic.getInstanceId());
        if (vm == null || vm.getType() != VirtualMachine.Type.User || vm.getRemoved() != null) {
            throw new InvalidParameterValueException("Subnets can only be routed to NICs of Instances");
        }
        accountManager.checkAccess(caller, AccessType.OperateEntry, true, vm);
        final NetworkVO network = networkDao.findById(nic.getNetworkId());
        if (network == null || !isSupportedNetwork(network)) {
            throw new InvalidParameterValueException("Subnets can only be routed to NICs on Direct Routed (L3) or Shared networks");
        }
        final long zoneId = network.getDataCenterId();
        if (!RoutedSubnetsEnabled.valueIn(zoneId)) {
            throw new InvalidParameterValueException("Routed subnets are disabled in this zone");
        }
        final Account owner = accountManager.getAccount(vm.getAccountId());

        if (cmd.getSubnetId() != null) {
            if (cmd.getOfferingId() != null) {
                throw new InvalidParameterValueException("Give either offeringid or subnetid, not both");
            }
            RoutedSubnet attached = attachExistingSubnet(cmd.getSubnetId(), nic, vm, owner, zoneId);
            applyOnHost(attached, nic, vm, network, true);
            return Collections.singletonList(attached);
        }
        if (cmd.getOfferingId() == null) {
            throw new InvalidParameterValueException("One of offeringid and subnetid is required");
        }
        final RoutedSubnetOfferingVO offering = offeringDao.findById(cmd.getOfferingId());
        if (offering == null || offering.getState() != RoutedSubnetOffering.State.Active) {
            throw new InvalidParameterValueException("Unable to find an active Routed Subnet Offering with id " + cmd.getOfferingId());
        }
        if (offering.getZoneId() != null && offering.getZoneId() != zoneId) {
            throw new InvalidParameterValueException("The Routed Subnet Offering is not available in the Instance's zone");
        }
        if (offering.getDomainId() != null && !isVisibleInDomain(offering.getDomainId(), owner.getDomainId())) {
            throw new InvalidParameterValueException("The Routed Subnet Offering is not available in the owner's domain");
        }
        validatePrefixLengths(offering.getIp4PrefixLength(), offering.getIp6PrefixLength(), zoneId);
        if (offering.getIp4PrefixLength() != null && StringUtils.isBlank(nic.getIPv4Address())) {
            throw new InvalidParameterValueException("The offering hands out an IPv4 subnet but the NIC has no IPv4 address");
        }
        if (offering.getIp6PrefixLength() != null && StringUtils.isBlank(nic.getIPv6Address())) {
            throw new InvalidParameterValueException("The offering hands out an IPv6 subnet but the NIC has no IPv6 address");
        }
        if (offering.getMode() == RoutedSubnetOffering.Mode.Static && getAnnouncer(zoneId) == null) {
            throw new InvalidParameterValueException("No route announcer is configured for the zone, so Static-mode offerings cannot be assigned");
        }
        final boolean admin = accountManager.isRootAdmin(caller.getId());
        final String wanted4 = admin ? cmd.getIp4Subnet() : null;
        final String wanted6 = admin ? cmd.getIp6Subnet() : null;
        if (wanted4 != null && (offering.getIp4PrefixLength() == null || !SubnetCarver.isValid(wanted4, 4) || SubnetCarver.prefixLength(wanted4) != offering.getIp4PrefixLength())) {
            throw new InvalidParameterValueException("ip4subnet must be a valid IPv4 subnet of the offering's prefix length");
        }
        if (wanted6 != null && (offering.getIp6PrefixLength() == null || !SubnetCarver.isValid(wanted6, 6) || SubnetCarver.prefixLength(wanted6) != offering.getIp6PrefixLength())) {
            throw new InvalidParameterValueException("ip6subnet must be a valid IPv6 subnet of the offering's prefix length");
        }

        int count = (offering.getIp4PrefixLength() != null ? 1 : 0) + (offering.getIp6PrefixLength() != null ? 1 : 0);
        resourceLimitService.checkResourceLimit(owner, ResourceType.routed_subnet, count);

        List<RoutedSubnet> subnets = Transaction.execute(new TransactionCallback<List<RoutedSubnet>>() {
            @Override
            public List<RoutedSubnet> doInTransaction(TransactionStatus status) {
                List<RoutedSubnet> result = new ArrayList<>();
                if (offering.getIp4PrefixLength() != null) {
                    result.add(allocateIpv4(owner, zoneId, offering, nic, wanted4));
                }
                if (offering.getIp6PrefixLength() != null) {
                    result.add(allocateIpv6(owner, zoneId, offering, nic, wanted6));
                }
                return result;
            }
        });
        resourceLimitService.incrementResourceCount(owner.getId(), ResourceType.routed_subnet, (long) count);
        for (RoutedSubnet subnet : subnets) {
            logger.info("Routed {} to {} of {} with offering {}", subnet.getSubnet(), nic, vm, offering.getName());
            applyOnHost(subnet, nic, vm, network, true);
            if (offering.getMode() == RoutedSubnetOffering.Mode.Static) {
                announceSubnet(subnet, nic, zoneId);
            }
        }
        return subnets;
    }

    protected boolean isSupportedNetwork(Network network) {
        return Network.GuestType.L3.equals(network.getGuestType()) || Network.GuestType.Shared.equals(network.getGuestType());
    }

    /** Pools the owner may draw from: dedicated to the account first, then to the domain, then shared. */
    private List<DataCenterIpv4GuestSubnetVO> ipv4PoolsFor(Account owner, long zoneId) {
        List<DataCenterIpv4GuestSubnetVO> pools = new ArrayList<>();
        pools.addAll(ipv4PoolDao.listByDataCenterIdAndAccountId(zoneId, owner.getId(), SubnetPoolPurpose.Instance));
        pools.addAll(ipv4PoolDao.listByDataCenterIdAndDomainId(zoneId, owner.getDomainId(), SubnetPoolPurpose.Instance));
        pools.addAll(ipv4PoolDao.listNonDedicatedByDataCenterId(zoneId, SubnetPoolPurpose.Instance));
        return pools;
    }

    private List<DataCenterGuestIpv6PrefixVO> ipv6PoolsFor(Account owner, long zoneId) {
        List<DataCenterGuestIpv6PrefixVO> pools = new ArrayList<>();
        pools.addAll(ipv6PoolDao.listByDataCenterIdAndAccountId(zoneId, owner.getId(), SubnetPoolPurpose.Instance));
        pools.addAll(ipv6PoolDao.listByDataCenterIdAndDomainId(zoneId, owner.getDomainId(), SubnetPoolPurpose.Instance));
        pools.addAll(ipv6PoolDao.listNonDedicatedByDataCenterId(zoneId, SubnetPoolPurpose.Instance));
        return pools;
    }

    private RoutedSubnet allocateIpv4(Account owner, long zoneId, RoutedSubnetOfferingVO offering, NicVO nic, String wanted) {
        int length = offering.getIp4PrefixLength();
        for (DataCenterIpv4GuestSubnetVO pool : ipv4PoolsFor(owner, zoneId)) {
            List<Ipv4GuestSubnetNetworkMapVO> children = ipv4MapDao.listByParent(pool.getId());
            String cidr;
            if (wanted != null) {
                cidr = SubnetCarver.normalize(wanted);
                if (!SubnetCarver.contains(pool.getSubnet(), cidr)) {
                    continue;
                }
                for (Ipv4GuestSubnetNetworkMapVO child : children) {
                    if (child.getSubnet().equals(cidr) && child.getState() == Ipv4GuestSubnetNetworkMap.State.Free) {
                        return claim(child, nic, offering, owner);
                    }
                    if (SubnetCarver.overlap(child.getSubnet(), cidr)) {
                        throw new InvalidParameterValueException(String.format("%s overlaps subnet %s already carved from the pool", cidr, child.getSubnet()));
                    }
                }
            } else {
                Ipv4GuestSubnetNetworkMapVO free = ipv4MapDao.findFirstAvailable(pool.getId(), length);
                if (free != null) {
                    return claim(free, nic, offering, owner);
                }
                List<String> taken = new ArrayList<>();
                for (Ipv4GuestSubnetNetworkMapVO child : children) {
                    taken.add(child.getSubnet());
                }
                cidr = SubnetCarver.carve(pool.getSubnet(), taken, length);
                if (cidr == null) {
                    continue;
                }
            }
            Ipv4GuestSubnetNetworkMapVO row = new Ipv4GuestSubnetNetworkMapVO(pool.getId(), pool.getDataCenterId(), cidr, null, Ipv4GuestSubnetNetworkMap.State.Free);
            row = ipv4MapDao.persist(row);
            return claim(row, nic, offering, owner);
        }
        if (wanted != null) {
            throw new InvalidParameterValueException(String.format("%s is not free in any Instance subnet pool the account may use in the zone", wanted));
        }
        throw new CloudRuntimeException(String.format("No free IPv4 /%d in the Instance subnet pools the account may use in the zone", length));
    }

    private RoutedSubnet allocateIpv6(Account owner, long zoneId, RoutedSubnetOfferingVO offering, NicVO nic, String wanted) {
        int length = offering.getIp6PrefixLength();
        for (DataCenterGuestIpv6PrefixVO pool : ipv6PoolsFor(owner, zoneId)) {
            List<Ipv6GuestPrefixSubnetNetworkMapVO> children = ipv6MapDao.listByPrefix(pool.getId());
            String cidr;
            if (wanted != null) {
                cidr = SubnetCarver.normalize(wanted);
                if (!SubnetCarver.contains(pool.getPrefix(), cidr)) {
                    continue;
                }
                for (Ipv6GuestPrefixSubnetNetworkMapVO child : children) {
                    if (child.getSubnet().equals(cidr) && child.getState() == Ipv6GuestPrefixSubnetNetworkMap.State.Free) {
                        return claim(child, nic, offering, owner);
                    }
                    if (SubnetCarver.overlap(child.getSubnet(), cidr)) {
                        throw new InvalidParameterValueException(String.format("%s overlaps subnet %s already carved from the prefix", cidr, child.getSubnet()));
                    }
                }
            } else {
                Ipv6GuestPrefixSubnetNetworkMapVO free = ipv6MapDao.findFirstAvailable(pool.getId(), length);
                if (free != null) {
                    return claim(free, nic, offering, owner);
                }
                List<String> taken = new ArrayList<>();
                for (Ipv6GuestPrefixSubnetNetworkMapVO child : children) {
                    taken.add(child.getSubnet());
                }
                cidr = SubnetCarver.carve(pool.getPrefix(), taken, length);
                if (cidr == null) {
                    continue;
                }
            }
            Ipv6GuestPrefixSubnetNetworkMapVO row = new Ipv6GuestPrefixSubnetNetworkMapVO(pool.getId(), cidr, null, Ipv6GuestPrefixSubnetNetworkMap.State.Free);
            row = ipv6MapDao.persist(row);
            return claim(row, nic, offering, owner);
        }
        if (wanted != null) {
            throw new InvalidParameterValueException(String.format("%s is not free in any Instance prefix the account may use in the zone", wanted));
        }
        throw new CloudRuntimeException(String.format("No free IPv6 /%d in the Instance prefixes the account may use in the zone", length));
    }

    private RoutedSubnet claim(Ipv4GuestSubnetNetworkMapVO row, NicVO nic, RoutedSubnetOfferingVO offering, Account owner) {
        row.setState(Ipv4GuestSubnetNetworkMap.State.Allocated);
        row.setAllocated(new Date());
        row.setNicId(nic.getId());
        row.setOfferingId(offering.getId());
        row.setAccountId(owner.getId());
        row.setDomainId(owner.getDomainId());
        ipv4MapDao.update(row.getId(), row);
        return new RoutedSubnetImpl(row);
    }

    private RoutedSubnet claim(Ipv6GuestPrefixSubnetNetworkMapVO row, NicVO nic, RoutedSubnetOfferingVO offering, Account owner) {
        row.setState(Ipv6GuestPrefixSubnetNetworkMap.State.Allocated);
        row.setUpdated(new Date());
        row.setNicId(nic.getId());
        row.setOfferingId(offering.getId());
        row.setAccountId(owner.getId());
        row.setDomainId(owner.getDomainId());
        ipv6MapDao.update(row.getId(), row);
        return new RoutedSubnetImpl(row);
    }

    /** Anycast: a second row with the same subnet, for another NIC of the same account. */
    private RoutedSubnet attachExistingSubnet(String subnetUuid, NicVO nic, VMInstanceVO vm, Account owner, long zoneId) {
        RoutedSubnetImpl existing = findByUuid(subnetUuid);
        if (existing == null || existing.getOfferingId() == null) {
            throw new InvalidParameterValueException("Unable to find routed subnet with id " + subnetUuid);
        }
        if (existing.getAccountId() != owner.getId()) {
            throw new InvalidParameterValueException("A subnet can only be shared between Instances of the same account");
        }
        RoutedSubnetOfferingVO offering = offeringDao.findById(existing.getOfferingId());
        if (offering == null || offering.getMode() != RoutedSubnetOffering.Mode.BGP) {
            throw new InvalidParameterValueException("Only BGP-mode subnets can be routed to several Instances");
        }
        int family = existing.getAddressFamily();
        if ((family == 4 && StringUtils.isBlank(nic.getIPv4Address())) || (family == 6 && StringUtils.isBlank(nic.getIPv6Address()))) {
            throw new InvalidParameterValueException("The NIC has no address of the subnet's family");
        }
        for (RoutedSubnetImpl row : rowsWithSubnet(family, existing.getSubnet())) {
            if (nic.getId() == (row.getNicId() == null ? -1 : row.getNicId())) {
                throw new InvalidParameterValueException("The subnet is already routed to this NIC");
            }
        }
        if (family == 4) {
            Ipv4GuestSubnetNetworkMapVO row = new Ipv4GuestSubnetNetworkMapVO(existing.getParentId(), zoneId, existing.getSubnet(), null, Ipv4GuestSubnetNetworkMap.State.Free);
            row = ipv4MapDao.persist(row);
            return claim(row, nic, offering, owner);
        }
        Ipv6GuestPrefixSubnetNetworkMapVO row = new Ipv6GuestPrefixSubnetNetworkMapVO(existing.getParentId(), existing.getSubnet(), null, Ipv6GuestPrefixSubnetNetworkMap.State.Free);
        row = ipv6MapDao.persist(row);
        return claim(row, nic, offering, owner);
    }

    // ---------------------------------------------------------------- release

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_ROUTED_SUBNET_RELEASE, eventDescription = "releasing a routed subnet", async = true)
    public boolean releaseRoutedSubnet(ReleaseRoutedSubnetCmd cmd) {
        Account caller = CallContext.current().getCallingAccount();
        RoutedSubnetImpl subnet = findByUuid(cmd.getId());
        if (subnet == null || subnet.getOfferingId() == null) {
            throw new InvalidParameterValueException("Unable to find routed subnet with id " + cmd.getId());
        }
        accountManager.checkAccess(caller, AccessType.OperateEntry, true, subnet);
        List<RoutedSubnetImpl> rows = rowsWithSubnet(subnet.getAddressFamily(), subnet.getSubnet());
        RoutedSubnetImpl target = subnet;
        if (cmd.getNicId() != null) {
            target = null;
            for (RoutedSubnetImpl row : rows) {
                if (row.getNicId() != null && row.getNicId().equals(cmd.getNicId())) {
                    target = row;
                }
            }
            if (target == null) {
                throw new InvalidParameterValueException("The subnet is not routed to NIC " + cmd.getNicId());
            }
        } else if (rows.size() > 1) {
            throw new InvalidParameterValueException("The subnet is routed to several NICs; give nicid");
        }
        release(target, true);
        return true;
    }

    /**
     * Withdraws the subnet's announcements and frees the row, or leaves it Releasing when an
     * endpoint could not be reached. The resource count drops when the account's last
     * attachment of the subnet goes.
     */
    protected void release(RoutedSubnetImpl subnet, boolean decrementCount) {
        NicVO nic = subnet.getNicId() == null ? null : nicDao.findByIdIncludingRemoved(subnet.getNicId());
        if (nic != null && nic.getRemoved() == null) {
            VMInstanceVO vm = vmInstanceDao.findById(nic.getInstanceId());
            NetworkVO network = networkDao.findById(nic.getNetworkId());
            if (vm != null && network != null) {
                applyOnHost(subnet, nic, vm, network, false);
            }
        }
        boolean clean = withdrawSubnet(subnet, nic);
        List<RoutedSubnetImpl> siblings = rowsWithSubnet(subnet.getAddressFamily(), subnet.getSubnet());
        boolean last = siblings.size() <= 1;
        if (clean) {
            free(subnet, last);
        } else {
            markReleasing(subnet);
        }
        if (decrementCount && last && subnet.getAccountId() > 0) {
            resourceLimitService.decrementResourceCount(subnet.getAccountId(), ResourceType.routed_subnet, 1L);
        }
        logger.info("Released {} from NIC {}{}", subnet.getSubnet(), subnet.getNicId(), clean ? "" : " (withdraw pending on an announcer endpoint)");
    }

    private void markReleasing(RoutedSubnetImpl subnet) {
        if (subnet.getV4() != null) {
            Ipv4GuestSubnetNetworkMapVO row = subnet.getV4();
            row.setState(Ipv4GuestSubnetNetworkMap.State.Releasing);
            ipv4MapDao.update(row.getId(), row);
        } else {
            Ipv6GuestPrefixSubnetNetworkMapVO row = subnet.getV6();
            row.setState(Ipv6GuestPrefixSubnetNetworkMap.State.Releasing);
            row.setUpdated(new Date());
            ipv6MapDao.update(row.getId(), row);
        }
    }

    /** Back to the pool as Free, or gone when other rows still carry the subnet (anycast). */
    private void free(RoutedSubnetImpl subnet, boolean last) {
        announcementDao.removeBySubnet(subnet.getAddressFamily(), subnet.getId());
        if (subnet.getV4() != null) {
            Ipv4GuestSubnetNetworkMapVO row = subnet.getV4();
            if (!last) {
                ipv4MapDao.remove(row.getId());
                return;
            }
            row.setState(Ipv4GuestSubnetNetworkMap.State.Free);
            row.setAllocated(null);
            row.setNicId(null);
            row.setOfferingId(null);
            row.setAccountId(null);
            row.setDomainId(null);
            ipv4MapDao.update(row.getId(), row);
        } else {
            Ipv6GuestPrefixSubnetNetworkMapVO row = subnet.getV6();
            if (!last) {
                ipv6MapDao.remove(row.getId());
                return;
            }
            row.setState(Ipv6GuestPrefixSubnetNetworkMap.State.Free);
            row.setUpdated(new Date());
            row.setNicId(null);
            row.setOfferingId(null);
            row.setAccountId(null);
            row.setDomainId(null);
            ipv6MapDao.update(row.getId(), row);
        }
    }

    @Override
    public void releaseRoutedSubnetsForNic(long nicId) {
        try {
            for (RoutedSubnet subnet : listRoutedSubnetsForNic(nicId)) {
                release((RoutedSubnetImpl) subnet, true);
            }
        } catch (RuntimeException e) {
            logger.warn("Failed to release the subnets routed to NIC {}; the reconciler will retry", nicId, e);
        }
    }

    @Override
    public void releaseRoutedSubnetsForAccount(long accountId) {
        try {
            List<RoutedSubnetImpl> rows = new ArrayList<>();
            for (Ipv4GuestSubnetNetworkMapVO row : ipv4MapDao.listByAccountId(accountId)) {
                rows.add(new RoutedSubnetImpl(row));
            }
            for (Ipv6GuestPrefixSubnetNetworkMapVO row : ipv6MapDao.listByAccountId(accountId)) {
                rows.add(new RoutedSubnetImpl(row));
            }
            for (RoutedSubnetImpl row : rows) {
                release(row, false);
            }
        } catch (RuntimeException e) {
            logger.warn("Failed to release the routed subnets of account {}", accountId, e);
        }
    }

    /**
     * Installs or removes the subnet on the host of a running Instance: the kernel route on a
     * Direct Routed host, and the security group ipset entry wherever rules apply to the
     * Instance. A stopped Instance gets it at the next start through its NicTO. Best effort: a
     * failure is logged, since the next start or rules refresh repairs it.
     */
    protected void applyOnHost(RoutedSubnet subnet, NicVO nic, VMInstanceVO vm, NetworkVO network, boolean add) {
        if (vm.getState() != VirtualMachine.State.Running || vm.getHostId() == null) {
            return;
        }
        boolean directRouted = Network.GuestType.L3.equals(network.getGuestType());
        boolean applySecurityGroupRules = networkModel.isSecurityGroupSupportedInNetwork(network)
                && CollectionUtils.isNotEmpty(securityGroupManager.getSecurityGroupsForVm(vm.getId()));
        if (!directRouted && !applySecurityGroupRules) {
            return;
        }
        String nextHop = nextHopOf(subnet, nic);
        if (nextHop == null) {
            return;
        }
        ModifyRoutedSubnetCommand command = new ModifyRoutedSubnetCommand(vm.getInstanceName(), nic.getMacAddress(), subnet.getSubnet(), nextHop,
                NetUtils.routedSubnetMetric(nextHop), add, directRouted, applySecurityGroupRules);
        try {
            Answer answer = agentManager.send(vm.getHostId(), command);
            if (answer == null || !answer.getResult()) {
                logger.warn("Host {} could not {} routed subnet {} for {}: {}", vm.getHostId(), add ? "add" : "remove", subnet.getSubnet(), vm,
                        answer == null ? "no answer" : answer.getDetails());
            }
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            logger.warn("Host {} unreachable while applying routed subnet {} for {}: {}", vm.getHostId(), subnet.getSubnet(), vm, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- lookups

    protected RoutedSubnetImpl findByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        Ipv4GuestSubnetNetworkMapVO v4 = ipv4MapDao.findByUuid(uuid);
        if (v4 != null) {
            return new RoutedSubnetImpl(v4);
        }
        Ipv6GuestPrefixSubnetNetworkMapVO v6 = ipv6MapDao.findByUuid(uuid);
        return v6 == null ? null : new RoutedSubnetImpl(v6);
    }

    /** Every Allocated or Releasing row carrying the subnet: one, or several for anycast. */
    protected List<RoutedSubnetImpl> rowsWithSubnet(int family, String subnet) {
        List<RoutedSubnetImpl> rows = new ArrayList<>();
        if (family == 4) {
            for (Ipv4GuestSubnetNetworkMapVO row : ipv4MapDao.listBySubnet(subnet)) {
                if (row.getOfferingId() != null && row.getState() != Ipv4GuestSubnetNetworkMap.State.Free) {
                    rows.add(new RoutedSubnetImpl(row));
                }
            }
        } else {
            for (Ipv6GuestPrefixSubnetNetworkMapVO row : ipv6MapDao.listBySubnet(subnet)) {
                if (row.getOfferingId() != null && row.getState() != Ipv6GuestPrefixSubnetNetworkMap.State.Free) {
                    rows.add(new RoutedSubnetImpl(row));
                }
            }
        }
        return rows;
    }

    @Override
    public List<RoutedSubnet> listRoutedSubnetsForNic(long nicId) {
        List<RoutedSubnet> rows = new ArrayList<>();
        for (Ipv4GuestSubnetNetworkMapVO row : ipv4MapDao.listByNicId(nicId)) {
            rows.add(new RoutedSubnetImpl(row));
        }
        for (Ipv6GuestPrefixSubnetNetworkMapVO row : ipv6MapDao.listByNicId(nicId)) {
            rows.add(new RoutedSubnetImpl(row));
        }
        return rows;
    }

    /** Every Instance subnet in the system that is not Free. */
    protected List<RoutedSubnetImpl> listAllInstanceSubnets() {
        List<RoutedSubnetImpl> rows = new ArrayList<>();
        for (Ipv4GuestSubnetNetworkMapVO row : ipv4MapDao.findSubnetsInStates(Ipv4GuestSubnetNetworkMap.State.Allocated, Ipv4GuestSubnetNetworkMap.State.Releasing)) {
            if (row.getOfferingId() != null) {
                rows.add(new RoutedSubnetImpl(row));
            }
        }
        for (Ipv6GuestPrefixSubnetNetworkMapVO row : ipv6MapDao.findPrefixesInStates(Ipv6GuestPrefixSubnetNetworkMap.State.Allocated, Ipv6GuestPrefixSubnetNetworkMap.State.Releasing)) {
            if (row.getOfferingId() != null) {
                rows.add(new RoutedSubnetImpl(row));
            }
        }
        return rows;
    }

    protected Long zoneOf(RoutedSubnet subnet, NicVO nic) {
        if (nic != null) {
            NetworkVO network = networkDao.findById(nic.getNetworkId());
            if (network != null) {
                return network.getDataCenterId();
            }
        }
        if (subnet.getParentId() == null) {
            return null;
        }
        if (subnet.getAddressFamily() == 4) {
            DataCenterIpv4GuestSubnetVO pool = ipv4PoolDao.findById(subnet.getParentId());
            return pool == null ? null : pool.getDataCenterId();
        }
        DataCenterGuestIpv6PrefixVO pool = ipv6PoolDao.findById(subnet.getParentId());
        return pool == null ? null : pool.getDataCenterId();
    }

    protected String nextHopOf(RoutedSubnet subnet, NicVO nic) {
        if (nic == null) {
            return null;
        }
        return subnet.getAddressFamily() == 4 ? nic.getIPv4Address() : nic.getIPv6Address();
    }

    @Override
    public Pair<List<RoutedSubnet>, Integer> listRoutedSubnets(ListRoutedSubnetsCmd cmd) {
        Account caller = CallContext.current().getCallingAccount();
        List<Long> permittedAccounts = new ArrayList<>();
        Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(cmd.getDomainId(), cmd.isRecursive(), null);
        accountManager.buildACLSearchParameters(caller, null, cmd.getAccountName(), cmd.getProjectId(), permittedAccounts, domainIdRecursiveListProject, cmd.listAll(), false);
        Long domainId = domainIdRecursiveListProject.first();
        boolean recursive = Boolean.TRUE.equals(domainIdRecursiveListProject.second());

        List<RoutedSubnetImpl> candidates;
        if (cmd.getId() != null) {
            RoutedSubnetImpl one = findByUuid(cmd.getId());
            candidates = one == null || one.getOfferingId() == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(one));
        } else if (cmd.getNicId() != null) {
            candidates = new ArrayList<>();
            for (RoutedSubnet s : listRoutedSubnetsForNic(cmd.getNicId())) {
                candidates.add((RoutedSubnetImpl) s);
            }
        } else if (cmd.getVirtualMachineId() != null) {
            candidates = new ArrayList<>();
            for (NicVO nic : nicDao.listByVmId(cmd.getVirtualMachineId())) {
                for (RoutedSubnet s : listRoutedSubnetsForNic(nic.getId())) {
                    candidates.add((RoutedSubnetImpl) s);
                }
            }
        } else {
            candidates = listAllInstanceSubnets();
        }

        RoutedSubnetOffering.Mode mode = cmd.getMode() == null ? null : RoutedSubnetOffering.Mode.fromString(cmd.getMode());
        Map<Long, RoutedSubnetOfferingVO> offerings = new HashMap<>();
        List<RoutedSubnet> result = new ArrayList<>();
        for (RoutedSubnetImpl subnet : candidates) {
            if (!permittedAccounts.isEmpty() && !permittedAccounts.contains(subnet.getAccountId())) {
                continue;
            }
            if (permittedAccounts.isEmpty() && domainId != null) {
                if (subnet.getDomainId() != domainId && !(recursive && domainDao.isChildDomain(domainId, subnet.getDomainId()))) {
                    continue;
                }
            }
            if (cmd.getSubnet() != null && !cmd.getSubnet().equals(subnet.getSubnet())) {
                continue;
            }
            if (mode != null) {
                RoutedSubnetOfferingVO offering = offerings.computeIfAbsent(subnet.getOfferingId(), offeringDao::findById);
                if (offering == null || offering.getMode() != mode) {
                    continue;
                }
            }
            if (cmd.getZoneId() != null) {
                NicVO nic = subnet.getNicId() == null ? null : nicDao.findById(subnet.getNicId());
                Long zoneId = zoneOf(subnet, nic);
                if (zoneId == null || !zoneId.equals(cmd.getZoneId())) {
                    continue;
                }
            }
            result.add(subnet);
        }
        Filter filter = new Filter(Ipv4GuestSubnetNetworkMapVO.class, "id", true, cmd.getStartIndex(), cmd.getPageSizeVal());
        return page(result, filter);
    }

    // ---------------------------------------------------------------- announcements

    protected RouteAnnouncer getAnnouncer(long zoneId) {
        String name = RoutedSubnetAnnouncer.valueIn(zoneId);
        if (StringUtils.isBlank(name)) {
            return null;
        }
        for (RouteAnnouncer announcer : routeAnnouncers) {
            if (name.trim().equalsIgnoreCase(announcer.getName())) {
                return announcer;
            }
        }
        logger.warn("Zone {} names route announcer {} but no such plugin is loaded", zoneId, name);
        return null;
    }

    protected boolean isStatic(RoutedSubnet subnet) {
        if (subnet.getOfferingId() == null) {
            return false;
        }
        RoutedSubnetOfferingVO offering = offeringDao.findById(subnet.getOfferingId());
        return offering != null && offering.getMode() == RoutedSubnetOffering.Mode.Static;
    }

    /**
     * Asks every endpoint of the zone to hold the route, recording per endpoint what happened.
     * With the failover strategy the first endpoint that accepts is enough. Never throws: a
     * subnet whose announcement failed stays assigned and the reconciler retries.
     */
    protected void announceSubnet(RoutedSubnet subnet, NicVO nic, long zoneId) {
        RouteAnnouncer announcer = getAnnouncer(zoneId);
        String nextHop = nextHopOf(subnet, nic);
        if (announcer == null || nextHop == null) {
            return;
        }
        boolean failover = STRATEGY_FAILOVER.equalsIgnoreCase(RoutedSubnetAnnouncerStrategy.valueIn(zoneId));
        for (String endpoint : announcer.getEndpoints(zoneId)) {
            boolean ok = announceTo(announcer, zoneId, endpoint, subnet, nextHop);
            if (ok && failover) {
                break;
            }
        }
    }

    private boolean announceTo(RouteAnnouncer announcer, long zoneId, String endpoint, RoutedSubnet subnet, String nextHop) {
        RoutedSubnetAnnouncementVO record = announcementDao.findBySubnetAndEndpoint(subnet.getAddressFamily(), subnet.getId(), endpoint);
        if (record == null) {
            record = announcementDao.persist(new RoutedSubnetAnnouncementVO(subnet.getAddressFamily(), subnet.getId(), endpoint));
        }
        record.setLastAttempt(new Date());
        try {
            announcer.announce(zoneId, endpoint, subnet.getSubnet(), nextHop);
            record.setAnnounced(new Date());
            record.setLastError(null);
            announcementDao.update(record.getId(), record);
            return true;
        } catch (RouteAnnouncementException | RuntimeException e) {
            logger.warn("Announcing {} via {} to {} failed: {}", subnet.getSubnet(), nextHop, endpoint, e.getMessage());
            record.setAnnounced(null);
            record.setLastError(e.getMessage());
            announcementDao.update(record.getId(), record);
            return false;
        }
    }

    /** Withdraws from every endpoint that has a record; true when none is left. */
    protected boolean withdrawSubnet(RoutedSubnetImpl subnet, NicVO nic) {
        List<RoutedSubnetAnnouncementVO> records = announcementDao.listBySubnet(subnet.getAddressFamily(), subnet.getId());
        if (records.isEmpty()) {
            return true;
        }
        Long zoneId = zoneOf(subnet, nic);
        RouteAnnouncer announcer = zoneId == null ? null : getAnnouncer(zoneId);
        String nextHop = nextHopOf(subnet, nic);
        boolean clean = true;
        for (RoutedSubnetAnnouncementVO record : records) {
            if (record.getAnnounced() == null) {
                announcementDao.remove(record.getId());
                continue;
            }
            if (announcer == null) {
                clean = false;
                continue;
            }
            record.setLastAttempt(new Date());
            try {
                announcer.withdraw(zoneId, record.getEndpoint(), subnet.getSubnet(), nextHop);
                announcementDao.remove(record.getId());
            } catch (RouteAnnouncementException | RuntimeException e) {
                logger.warn("Withdrawing {} from {} failed: {}", subnet.getSubnet(), record.getEndpoint(), e.getMessage());
                record.setLastError(e.getMessage());
                announcementDao.update(record.getId(), record);
                clean = false;
            }
        }
        return clean;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_ROUTED_SUBNET_REANNOUNCE, eventDescription = "re-announcing a routed subnet", async = true)
    public RoutedSubnet reannounceRoutedSubnet(ReannounceRoutedSubnetCmd cmd) {
        RoutedSubnetImpl subnet = findByUuid(cmd.getId());
        if (subnet == null || subnet.getOfferingId() == null) {
            throw new InvalidParameterValueException("Unable to find routed subnet with id " + cmd.getId());
        }
        if (!isStatic(subnet)) {
            throw new InvalidParameterValueException("Only Static-mode subnets are announced by the management server");
        }
        NicVO nic = subnet.getNicId() == null ? null : nicDao.findById(subnet.getNicId());
        Long zoneId = zoneOf(subnet, nic);
        if (zoneId == null) {
            throw new CloudRuntimeException("Unable to determine the zone of the subnet");
        }
        announceSubnet(subnet, nic, zoneId);
        return findByUuid(cmd.getId());
    }

    @Override
    public void reannounceRoutedSubnetsForNetwork(long networkId) {
        for (NicVO nic : nicDao.listByNetworkId(networkId)) {
            for (RoutedSubnet subnet : listRoutedSubnetsForNic(nic.getId())) {
                if (!isStatic(subnet) || !"Allocated".equals(subnet.getState())) {
                    continue;
                }
                Long zoneId = zoneOf(subnet, nic);
                if (zoneId != null) {
                    announceSubnet(subnet, nic, zoneId);
                }
            }
        }
    }

    /**
     * Makes one endpoint hold exactly what the database says: announce what it lacks, withdraw
     * what the database does not know, and finish Releasing rows once nothing holds them.
     */
    protected void syncEndpoint(long zoneId, RouteAnnouncer announcer, String endpoint) {
        Set<Announcement> held;
        try {
            held = new HashSet<>(announcer.list(zoneId, endpoint));
        } catch (RouteAnnouncementException | RuntimeException e) {
            logger.debug("Announcer endpoint {} of zone {} not reachable for reconciliation: {}", endpoint, zoneId, e.getMessage());
            return;
        }
        Map<Announcement, RoutedSubnetImpl> desired = new LinkedHashMap<>();
        List<RoutedSubnetImpl> releasing = new ArrayList<>();
        for (RoutedSubnetImpl subnet : listAllInstanceSubnets()) {
            NicVO nic = subnet.getNicId() == null ? null : nicDao.findByIdIncludingRemoved(subnet.getNicId());
            Long subnetZone = zoneOf(subnet, nic);
            if (subnetZone == null || subnetZone != zoneId || !isStatic(subnet)) {
                continue;
            }
            if ("Releasing".equals(subnet.getState())) {
                releasing.add(subnet);
                continue;
            }
            String nextHop = nextHopOf(subnet, nic);
            if (nextHop != null) {
                desired.put(new Announcement(subnet.getSubnet(), nextHop), subnet);
            }
        }
        for (Map.Entry<Announcement, RoutedSubnetImpl> entry : desired.entrySet()) {
            RoutedSubnetAnnouncementVO record = announcementDao.findBySubnetAndEndpoint(entry.getValue().getAddressFamily(), entry.getValue().getId(), endpoint);
            if (held.contains(entry.getKey()) && record != null && record.getAnnounced() != null) {
                continue;
            }
            announceTo(announcer, zoneId, endpoint, entry.getValue(), entry.getKey().getNextHop());
        }
        for (Announcement extra : held) {
            if (desired.containsKey(extra)) {
                continue;
            }
            try {
                announcer.withdraw(zoneId, endpoint, extra.getPrefix(), extra.getNextHop());
                logger.info("Withdrew {} from {}: the database does not know it", extra, endpoint);
            } catch (RouteAnnouncementException | RuntimeException e) {
                logger.warn("Withdrawing {} from {} failed: {}", extra, endpoint, e.getMessage());
            }
        }
        for (RoutedSubnetImpl subnet : releasing) {
            RoutedSubnetAnnouncementVO record = announcementDao.findBySubnetAndEndpoint(subnet.getAddressFamily(), subnet.getId(), endpoint);
            if (record != null) {
                announcementDao.remove(record.getId());
            }
            if (announcementDao.listBySubnet(subnet.getAddressFamily(), subnet.getId()).isEmpty()) {
                List<RoutedSubnetImpl> siblings = rowsWithSubnet(subnet.getAddressFamily(), subnet.getSubnet());
                free(subnet, siblings.size() <= 1);
                logger.info("Released {} now that no announcer endpoint holds it", subnet.getSubnet());
            }
        }
    }

    protected void reconcileAll() {
        for (DataCenterVO zone : zoneDao.listAll()) {
            RouteAnnouncer announcer = getAnnouncer(zone.getId());
            if (announcer == null) {
                continue;
            }
            for (String endpoint : announcer.getEndpoints(zone.getId())) {
                syncEndpoint(zone.getId(), announcer, endpoint);
            }
        }
    }

    /** Polls every endpoint; one that comes back, or restarted, is resynchronised at once. */
    protected void checkHealth() {
        for (DataCenterVO zone : zoneDao.listAll()) {
            RouteAnnouncer announcer = getAnnouncer(zone.getId());
            if (announcer == null) {
                continue;
            }
            for (String endpoint : announcer.getEndpoints(zone.getId())) {
                String key = zone.getId() + "|" + endpoint;
                String previous = endpointInstanceIds.get(key);
                String current;
                try {
                    current = announcer.getInstanceId(zone.getId(), endpoint);
                } catch (RouteAnnouncementException | RuntimeException e) {
                    if (previous != null) {
                        logger.warn("Route announcer endpoint {} of zone {} became unreachable: {}", endpoint, zone.getName(), e.getMessage());
                    }
                    endpointInstanceIds.remove(key);
                    continue;
                }
                if (current == null) {
                    current = "";
                }
                if (previous == null || !previous.equals(current)) {
                    if (previous != null) {
                        logger.warn("Route announcer endpoint {} of zone {} restarted (instance {} -> {}); replaying its routes", endpoint, zone.getName(), previous, current);
                    } else {
                        logger.info("Route announcer endpoint {} of zone {} is reachable (instance {}); synchronising", endpoint, zone.getName(), current);
                    }
                    endpointInstanceIds.put(key, current);
                    syncEndpoint(zone.getId(), announcer, endpoint);
                }
            }
        }
    }

    protected class ReconcileTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            GlobalLock lock = GlobalLock.getInternLock("RoutedSubnet.Reconcile");
            try {
                if (lock.lock(3)) {
                    try {
                        reconcileAll();
                    } catch (Exception e) {
                        logger.warn("Routed subnet reconciliation failed", e);
                    } finally {
                        lock.unlock();
                    }
                }
            } finally {
                lock.releaseRef();
            }
        }
    }

    protected class HealthTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            GlobalLock lock = GlobalLock.getInternLock("RoutedSubnet.Health");
            try {
                if (lock.lock(3)) {
                    try {
                        checkHealth();
                    } catch (Exception e) {
                        logger.warn("Routed subnet announcer health check failed", e);
                    } finally {
                        lock.unlock();
                    }
                }
            } finally {
                lock.releaseRef();
            }
        }
    }

    // ---------------------------------------------------------------- responses

    @Override
    public RoutedSubnetResponse createRoutedSubnetResponse(RoutedSubnet subnet) {
        RoutedSubnetResponse response = new RoutedSubnetResponse();
        response.setId(subnet.getUuid());
        response.setSubnet(subnet.getSubnet());
        response.setAddressFamily(subnet.getAddressFamily());
        response.setState(subnet.getState());
        response.setCreated(subnet.getAllocated() != null ? subnet.getAllocated() : subnet.getCreated());

        RoutedSubnetOfferingVO offering = subnet.getOfferingId() == null ? null : offeringDao.findById(subnet.getOfferingId());
        boolean isStatic = offering != null && offering.getMode() == RoutedSubnetOffering.Mode.Static;
        if (offering != null) {
            response.setOfferingId(offering.getUuid());
            response.setOfferingName(offering.getName());
            response.setMode(offering.getMode().name());
        }

        NicVO nic = subnet.getNicId() == null ? null : nicDao.findByIdIncludingRemoved(subnet.getNicId());
        fillNic(response, subnet, nic);
        Long zoneId = zoneOf(subnet, nic);
        if (zoneId != null) {
            DataCenter zone = zoneDao.findById(zoneId);
            if (zone != null) {
                response.setZoneId(zone.getUuid());
                response.setZoneName(zone.getName());
            }
        }

        List<RoutedSubnetNicResponse> nics = new ArrayList<>();
        for (RoutedSubnetImpl row : rowsWithSubnet(subnet.getAddressFamily(), subnet.getSubnet())) {
            if (row.getAccountId() != subnet.getAccountId()) {
                continue;
            }
            RoutedSubnetNicResponse nicResponse = new RoutedSubnetNicResponse();
            nicResponse.setId(row.getUuid());
            nicResponse.setState(row.getState());
            NicVO rowNic = row.getNicId() == null ? null : nicDao.findByIdIncludingRemoved(row.getNicId());
            if (rowNic != null) {
                nicResponse.setNicId(rowNic.getUuid());
                nicResponse.setNextHop(nextHopOf(row, rowNic));
                VMInstanceVO vm = vmInstanceDao.findByIdIncludingRemoved(rowNic.getInstanceId());
                if (vm != null) {
                    nicResponse.setVirtualMachineId(vm.getUuid());
                    nicResponse.setVirtualMachineName(vm.getHostName());
                }
                NetworkVO network = networkDao.findById(rowNic.getNetworkId());
                if (network != null) {
                    nicResponse.setNetworkId(network.getUuid());
                }
            }
            if (isStatic) {
                nicResponse.setAnnounced(announcedState(row));
            }
            nicResponse.setObjectName("nic");
            nics.add(nicResponse);
        }
        response.setNics(nics);

        if (isStatic) {
            response.setAnnounced(announcedState(subnet));
            List<RoutedSubnetEndpointResponse> endpoints = new ArrayList<>();
            for (RoutedSubnetAnnouncementVO record : announcementDao.listBySubnet(subnet.getAddressFamily(), subnet.getId())) {
                RoutedSubnetEndpointResponse endpoint = new RoutedSubnetEndpointResponse();
                endpoint.setEndpoint(record.getEndpoint());
                endpoint.setAnnounced(record.getAnnounced());
                endpoint.setLastAttempt(record.getLastAttempt());
                endpoint.setLastError(record.getLastError());
                endpoint.setObjectName("endpoint");
                endpoints.add(endpoint);
            }
            response.setEndpoints(endpoints);
        }

        if (subnet.getAccountId() > 0) {
            Account account = accountDao.findByIdIncludingRemoved(subnet.getAccountId());
            if (account != null) {
                if (account.getType() == Account.Type.PROJECT) {
                    Project project = projectDao.findByProjectAccountId(account.getId());
                    if (project != null) {
                        response.setProjectId(project.getUuid());
                        response.setProjectName(project.getName());
                    }
                } else {
                    response.setAccountName(account.getAccountName());
                }
            }
        }
        if (subnet.getDomainId() > 0) {
            Domain domain = domainDao.findByIdIncludingRemoved(subnet.getDomainId());
            if (domain != null) {
                response.setDomainId(domain.getUuid());
                response.setDomainName(domain.getName());
                response.setDomainPath(domain.getPath());
            }
        }
        response.setObjectName("routedsubnet");
        return response;
    }

    private void fillNic(RoutedSubnetResponse response, RoutedSubnet subnet, NicVO nic) {
        if (nic == null) {
            return;
        }
        response.setNicId(nic.getUuid());
        response.setNextHop(nextHopOf(subnet, nic));
        VMInstanceVO vm = vmInstanceDao.findByIdIncludingRemoved(nic.getInstanceId());
        if (vm != null) {
            response.setVirtualMachineId(vm.getUuid());
            response.setVirtualMachineName(vm.getHostName());
        }
        NetworkVO network = networkDao.findById(nic.getNetworkId());
        if (network != null) {
            response.setNetworkId(network.getUuid());
        }
    }

    /** none, partial or all: how many of the subnet's endpoint records are held. */
    protected String announcedState(RoutedSubnet subnet) {
        List<RoutedSubnetAnnouncementVO> records = announcementDao.listBySubnet(subnet.getAddressFamily(), subnet.getId());
        if (records.isEmpty()) {
            return "none";
        }
        int held = 0;
        for (RoutedSubnetAnnouncementVO record : records) {
            if (record.getAnnounced() != null) {
                held++;
            }
        }
        if (held == 0) {
            return "none";
        }
        Long zoneId = zoneOf(subnet, subnet.getNicId() == null ? null : nicDao.findByIdIncludingRemoved(subnet.getNicId()));
        boolean failover = zoneId != null && STRATEGY_FAILOVER.equalsIgnoreCase(RoutedSubnetAnnouncerStrategy.valueIn(zoneId));
        return failover || held == records.size() ? "all" : "partial";
    }
}
