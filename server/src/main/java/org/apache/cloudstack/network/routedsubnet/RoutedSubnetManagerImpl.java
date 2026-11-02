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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.api.command.admin.routedsubnet.CreateRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.DeleteRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.admin.routedsubnet.UpdateRoutedSubnetOfferingCmd;
import org.apache.cloudstack.api.command.user.routedsubnet.ListRoutedSubnetOfferingsCmd;
import org.apache.cloudstack.api.response.RoutedSubnetOfferingResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.network.dao.Ipv4GuestSubnetNetworkMapDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetOfferingDao;
import org.apache.cloudstack.network.routedsubnet.dao.RoutedSubnetOfferingDetailsDao;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.dao.Ipv6GuestPrefixSubnetNetworkMapDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;

public class RoutedSubnetManagerImpl extends ManagerBase implements RoutedSubnetService {

    @Inject
    private RoutedSubnetOfferingDao offeringDao;
    @Inject
    private RoutedSubnetOfferingDetailsDao offeringDetailsDao;
    @Inject
    private Ipv4GuestSubnetNetworkMapDao ipv4SubnetMapDao;
    @Inject
    private Ipv6GuestPrefixSubnetNetworkMapDao ipv6SubnetMapDao;
    @Inject
    private DataCenterDao zoneDao;
    @Inject
    private DomainDao domainDao;
    @Inject
    private AccountManager accountManager;

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
        return cmdList;
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
        if (CollectionUtils.isNotEmpty(ipv4SubnetMapDao.listByOfferingId(offering.getId()))
                || CollectionUtils.isNotEmpty(ipv6SubnetMapDao.listByOfferingId(offering.getId()))) {
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
        int total = visible.size();
        long start = filter.getOffset() == null ? 0 : filter.getOffset();
        long limit = filter.getLimit() == null ? total : filter.getLimit();
        List<RoutedSubnetOffering> page = new ArrayList<>();
        for (long i = start; i < total && i < start + limit; i++) {
            page.add(visible.get((int) i));
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

    // unused-import guards for types referenced only in later commits
    @SuppressWarnings("unused")
    private static final Class<?>[] REFERENCED = {DataCenterVO.class, DomainVO.class};
}
