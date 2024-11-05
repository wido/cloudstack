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
package org.apache.cloudstack.api;

import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cloud.bgp.ASNumber;
import com.cloud.bgp.ASNumberRange;

import org.apache.cloudstack.api.response.*;
import org.apache.cloudstack.dns.Zone;
import org.apache.cloudstack.storage.object.Bucket;
import org.apache.cloudstack.affinity.AffinityGroup;
import org.apache.cloudstack.affinity.AffinityGroupResponse;
import org.apache.cloudstack.api.ApiConstants.HostDetails;
import org.apache.cloudstack.api.ApiConstants.VMDetails;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.job.QueryAsyncJobResultCmd;
import org.apache.cloudstack.backup.Backup;
import org.apache.cloudstack.backup.BackupOffering;
import org.apache.cloudstack.backup.BackupRepository;
import org.apache.cloudstack.backup.BackupSchedule;
import org.apache.cloudstack.config.Configuration;
import org.apache.cloudstack.config.ConfigurationGroup;
import org.apache.cloudstack.direct.download.DirectDownloadCertificate;
import org.apache.cloudstack.direct.download.DirectDownloadCertificateHostMap;
import org.apache.cloudstack.direct.download.DirectDownloadManager;
import org.apache.cloudstack.management.ManagementServerHost;
import org.apache.cloudstack.network.lb.ApplicationLoadBalancerRule;
import org.apache.cloudstack.region.PortableIp;
import org.apache.cloudstack.region.PortableIpRange;
import org.apache.cloudstack.region.Region;
import org.apache.cloudstack.secstorage.heuristics.Heuristic;
import org.apache.cloudstack.storage.sharedfs.SharedFS;
import org.apache.cloudstack.storage.object.ObjectStore;
import org.apache.cloudstack.usage.Usage;

import com.cloud.capacity.Capacity;
import com.cloud.configuration.ResourceCount;
import com.cloud.configuration.ResourceLimit;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterGuestIpv6Prefix;
import com.cloud.dc.Pod;
import com.cloud.dc.StorageNetworkIpRange;
import com.cloud.dc.Vlan;
import com.cloud.domain.Domain;
import com.cloud.event.Event;
import com.cloud.host.Host;
import com.cloud.hypervisor.HypervisorCapabilities;
import com.cloud.network.GuestVlan;
import com.cloud.network.GuestVlanRange;
import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkPermission;
import com.cloud.network.Networks.IsolationType;
import com.cloud.network.OvsProvider;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PhysicalNetworkTrafficType;
import com.cloud.network.PublicIpQuarantine;
import com.cloud.network.RemoteAccessVpn;
import com.cloud.network.RouterHealthCheckResult;
import com.cloud.network.Site2SiteCustomerGateway;
import com.cloud.network.Site2SiteVpnConnection;
import com.cloud.network.Site2SiteVpnGateway;
import com.cloud.network.VirtualRouterProvider;
import com.cloud.network.VpnUser;
import com.cloud.network.as.AutoScalePolicy;
import com.cloud.network.as.AutoScaleVmGroup;
import com.cloud.network.as.AutoScaleVmProfile;
import com.cloud.network.as.Condition;
import com.cloud.network.as.Counter;
import com.cloud.network.router.VirtualRouter;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.HealthCheckPolicy;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.network.rules.PortForwardingRule;
import com.cloud.network.rules.StaticNatRule;
import com.cloud.network.rules.StickinessPolicy;
import com.cloud.network.security.SecurityGroup;
import com.cloud.network.security.SecurityRule;
import com.cloud.network.vpc.NetworkACL;
import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.network.vpc.PrivateGateway;
import com.cloud.network.vpc.StaticRoute;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.VpcOffering;
import com.cloud.offering.DiskOffering;
import com.cloud.offering.NetworkOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.org.Cluster;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectAccount;
import com.cloud.projects.ProjectInvitation;
import com.cloud.region.ha.GlobalLoadBalancerRule;
import com.cloud.resource.RollingMaintenanceManager;
import com.cloud.server.ResourceTag;
import com.cloud.server.ResourceIcon;
import com.cloud.storage.GuestOS;
import com.cloud.storage.GuestOSHypervisor;
import com.cloud.storage.ImageStore;
import com.cloud.storage.Snapshot;
import com.cloud.storage.StoragePool;
import com.cloud.storage.Volume;
import com.cloud.storage.snapshot.SnapshotPolicy;
import com.cloud.storage.snapshot.SnapshotSchedule;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.SSHKeyPair;
import com.cloud.user.User;
import com.cloud.user.UserAccount;
import com.cloud.user.UserData;
import com.cloud.uservm.UserVm;
import com.cloud.utils.net.Ip;
import com.cloud.utils.Pair;
import com.cloud.vm.InstanceGroup;
import com.cloud.vm.Nic;
import com.cloud.vm.NicSecondaryIp;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.snapshot.VMSnapshot;
import org.apache.cloudstack.vm.UnmanagedInstanceTO;

public interface ResponseGenerator {
    UserResponse createUserResponse(UserAccount user);

    AccountResponse createAccountResponse(ResponseView view, Account account);

    DomainResponse createDomainResponse(Domain domain);

    DiskOfferingResponse createDiskOfferingResponse(DiskOffering offering);

    ResourceLimitResponse createResourceLimitResponse(ResourceLimit limit);

    ResourceCountResponse createResourceCountResponse(ResourceCount resourceCount);

    ServiceOfferingResponse createServiceOfferingResponse(ServiceOffering offering);

    ConfigurationResponse createConfigurationResponse(Configuration cfg);

    ConfigurationGroupResponse createConfigurationGroupResponse(ConfigurationGroup cfgGroup);

    SnapshotResponse createSnapshotResponse(Snapshot snapshot);

    SnapshotPolicyResponse createSnapshotPolicyResponse(SnapshotPolicy policy);

    List<UserVmResponse> createUserVmResponse(ResponseView view, String objectName, UserVm... userVms);

    List<UserVmResponse> createUserVmResponse(ResponseView view, String objectName, EnumSet<VMDetails> details, UserVm... userVms);

    SystemVmResponse createSystemVmResponse(VirtualMachine systemVM);

    DomainRouterResponse createDomainRouterResponse(VirtualRouter router);

    HostResponse createHostResponse(Host host, EnumSet<HostDetails> details);

    HostResponse createHostResponse(Host host);

    HostForMigrationResponse createHostForMigrationResponse(Host host);

    HostForMigrationResponse createHostForMigrationResponse(Host host, EnumSet<HostDetails> details);

    VlanIpRangeResponse createVlanIpRangeResponse(Vlan vlan);

    VlanIpRangeResponse createVlanIpRangeResponse(Class<? extends VlanIpRangeResponse> subClass, Vlan vlan);

    IPAddressResponse createIPAddressResponse(ResponseView view, IpAddress ipAddress);

    GuestVlanRangeResponse createDedicatedGuestVlanRangeResponse(GuestVlanRange result);

    GlobalLoadBalancerResponse createGlobalLoadBalancerResponse(GlobalLoadBalancerRule globalLoadBalancerRule);

    LoadBalancerResponse createLoadBalancerResponse(LoadBalancer loadBalancer);

    LBStickinessResponse createLBStickinessPolicyResponse(List<? extends StickinessPolicy> stickinessPolicies, LoadBalancer lb);

    LBStickinessResponse createLBStickinessPolicyResponse(StickinessPolicy stickinessPolicy, LoadBalancer lb);

    LBHealthCheckResponse createLBHealthCheckPolicyResponse(List<? extends HealthCheckPolicy> healthcheckPolicies, LoadBalancer lb);

    LBHealthCheckResponse createLBHealthCheckPolicyResponse(HealthCheckPolicy healthcheckPolicy, LoadBalancer lb);

    PodResponse createPodResponse(Pod pod, Boolean showCapacities);

    ZoneResponse createZoneResponse(ResponseView view, DataCenter dataCenter, Boolean showCapacities, Boolean showResourceIcon);

    DataCenterGuestIpv6PrefixResponse createDataCenterGuestIpv6PrefixResponse(DataCenterGuestIpv6Prefix prefix);

    VolumeResponse createVolumeResponse(ResponseView view, Volume volume);

    InstanceGroupResponse createInstanceGroupResponse(InstanceGroup group);

    StoragePoolResponse createStoragePoolResponse(StoragePool pool);

    StoragePoolResponse createStoragePoolForMigrationResponse(StoragePool pool);

    ClusterResponse createClusterResponse(Cluster cluster, Boolean showCapacities);

    FirewallRuleResponse createPortForwardingRuleResponse(PortForwardingRule fwRule);

    IpForwardingRuleResponse createIpForwardingRuleResponse(StaticNatRule fwRule);

    User findUserById(Long userId);

    UserVm findUserVmById(Long vmId);

    Volume findVolumeById(Long volumeId);

    Account findAccountByNameDomain(String accountName, Long domainId);

    VirtualMachineTemplate findTemplateById(Long templateId);

    Host findHostById(Long hostId);

    DiskOffering findDiskOfferingById(Long diskOfferingId);

    VpnUsersResponse createVpnUserResponse(VpnUser user);

    RemoteAccessVpnResponse createRemoteAccessVpnResponse(RemoteAccessVpn vpn);

    List<TemplateResponse> createTemplateResponses(ResponseView view, long templateId, Long zoneId, boolean readyOnly);

    List<TemplateResponse> createTemplateResponses(ResponseView view, long templateId, Long snapshotId, Long volumeId, boolean readyOnly);

    SecurityGroupResponse createSecurityGroupResponseFromSecurityGroupRule(List<? extends SecurityRule> securityRules);

    SecurityGroupResponse createSecurityGroupResponse(SecurityGroup group);

    ExtractResponse createImageExtractResponse(Long id, Long zoneId, Long accountId, String mode, String url);

    ExtractResponse createVolumeExtractResponse(Long id, Long zoneId, Long accountId, String mode, String url);

    ExtractResponse createSnapshotExtractResponse(Long id, Long zoneId, Long accountId, String url);

    String toSerializedString(CreateCmdResponse response, String responseType);

    EventResponse createEventResponse(Event event);

    TemplateResponse createTemplateUpdateResponse(ResponseView view, VirtualMachineTemplate result);

    List<TemplateResponse> createTemplateResponses(ResponseView view, VirtualMachineTemplate result,
                                                   Long zoneId, boolean readyOnly);

    List<TemplateResponse> createTemplateResponses(ResponseView view, VirtualMachineTemplate result,
                                                   List<Long> zoneIds, boolean readyOnly);

    List<CapacityResponse> createCapacityResponse(List<? extends Capacity> result, DecimalFormat format);

    TemplatePermissionsResponse createTemplatePermissionsResponse(ResponseView view, List<String> accountNames, Long id);

    AsyncJobResponse queryJobResult(QueryAsyncJobResultCmd cmd);

    NetworkOfferingResponse createNetworkOfferingResponse(NetworkOffering offering);

    NetworkResponse createNetworkResponse(ResponseView view, Network network);

    UserResponse createUserResponse(User user);

    AccountResponse createUserAccountResponse(ResponseView view, UserAccount user);

    Long getSecurityGroupId(String groupName, long accountId);

    List<TemplateResponse> createIsoResponses(ResponseView view, VirtualMachineTemplate iso, Long zoneId, boolean readyOnly);

    ProjectResponse createProjectResponse(Project project);

    List<TemplateResponse> createTemplateResponses(ResponseView view, long templateId, Long vmId);

    FirewallResponse createFirewallResponse(FirewallRule fwRule);

    HypervisorCapabilitiesResponse createHypervisorCapabilitiesResponse(HypervisorCapabilities hpvCapabilities);

    ProjectAccountResponse createProjectAccountResponse(ProjectAccount projectAccount);

    ProjectInvitationResponse createProjectInvitationResponse(ProjectInvitation invite);

    SystemVmInstanceResponse createSystemVmInstanceResponse(VirtualMachine systemVM);

    PhysicalNetworkResponse createPhysicalNetworkResponse(PhysicalNetwork result);

    ServiceResponse createNetworkServiceResponse(Service service);

    ProviderResponse createNetworkServiceProviderResponse(PhysicalNetworkServiceProvider result);

    TrafficTypeResponse createTrafficTypeResponse(PhysicalNetworkTrafficType result);

    VirtualRouterProviderResponse createVirtualRouterProviderResponse(VirtualRouterProvider result);

    OvsProviderResponse createOvsProviderResponse(OvsProvider result);

    StorageNetworkIpRangeResponse createStorageNetworkIpRangeResponse(StorageNetworkIpRange result);

    RegionResponse createRegionResponse(Region region);

    ImageStoreResponse createImageStoreResponse(ImageStore os);

    /**
     * @param resourceTag
     * @param keyValueOnly TODO
     * @return
     */
    ResourceTagResponse createResourceTagResponse(ResourceTag resourceTag, boolean keyValueOnly);

    Site2SiteVpnGatewayResponse createSite2SiteVpnGatewayResponse(Site2SiteVpnGateway result);

    /**
     * @param offering
     * @return
     */
    VpcOfferingResponse createVpcOfferingResponse(VpcOffering offering);

    /**
     * @param vpc
     * @return
     */
    VpcResponse createVpcResponse(ResponseView view, Vpc vpc);

    /**
     * @param networkACLItem
     * @return
     */
    NetworkACLItemResponse createNetworkACLItemResponse(NetworkACLItem networkACLItem);

    /**
     * @param networkACL
     * @return
     */
    NetworkACLResponse createNetworkACLResponse(NetworkACL networkACL);

    /**
     * @param result
     * @return
     */
    PrivateGatewayResponse createPrivateGatewayResponse(ResponseView view, PrivateGateway result);

    /**
     * @param result
     * @return
     */
    StaticRouteResponse createStaticRouteResponse(StaticRoute result);

    Site2SiteCustomerGatewayResponse createSite2SiteCustomerGatewayResponse(Site2SiteCustomerGateway result);

    Site2SiteVpnConnectionResponse createSite2SiteVpnConnectionResponse(Site2SiteVpnConnection result);

    CounterResponse createCounterResponse(Counter ctr);

    ConditionResponse createConditionResponse(Condition cndn);

    AutoScalePolicyResponse createAutoScalePolicyResponse(AutoScalePolicy policy);

    AutoScaleVmProfileResponse createAutoScaleVmProfileResponse(AutoScaleVmProfile profile);

    AutoScaleVmGroupResponse createAutoScaleVmGroupResponse(AutoScaleVmGroup vmGroup);

    GuestOSResponse createGuestOSResponse(GuestOS os);

    GuestOsMappingResponse createGuestOSMappingResponse(GuestOSHypervisor osHypervisor);

    HypervisorGuestOsNamesResponse createHypervisorGuestOSNamesResponse(List<Pair<String, String>> hypervisorGuestOsNames);

    SnapshotScheduleResponse createSnapshotScheduleResponse(SnapshotSchedule sched);

    UsageRecordResponse createUsageResponse(Usage usageRecord);

    UsageRecordResponse createUsageResponse(Usage usageRecord, Map<String, Set<ResourceTagResponse>> resourceTagResponseMap, boolean oldFormat);

    public Map<String, Set<ResourceTagResponse>> getUsageResourceTags();

    TrafficMonitorResponse createTrafficMonitorResponse(Host trafficMonitor);

    VMSnapshotResponse createVMSnapshotResponse(VMSnapshot vmSnapshot);

    NicSecondaryIpResponse createSecondaryIPToNicResponse(NicSecondaryIp result);

    public NicResponse createNicResponse(Nic result);

    ApplicationLoadBalancerResponse createLoadBalancerContainerReponse(ApplicationLoadBalancerRule lb, Map<Ip, UserVm> lbInstances);

    AffinityGroupResponse createAffinityGroupResponse(AffinityGroup group);

    Long getAffinityGroupId(String name, long entityOwnerId);

    PortableIpRangeResponse createPortableIPRangeResponse(PortableIpRange range);

    PortableIpResponse createPortableIPResponse(PortableIp portableIp);

    InternalLoadBalancerElementResponse createInternalLbElementResponse(VirtualRouterProvider result);

    IsolationMethodResponse createIsolationMethodResponse(IsolationType method);

    ListResponse<UpgradeRouterTemplateResponse> createUpgradeRouterTemplateResponse(List<Long> jobIds);

    SSHKeyPairResponse createSSHKeyPairResponse(SSHKeyPair sshkeyPair, boolean privatekey);

    UserDataResponse createUserDataResponse(UserData userData);

    BackupResponse createBackupResponse(Backup backup);

    BackupScheduleResponse createBackupScheduleResponse(BackupSchedule backup);

    BackupOfferingResponse createBackupOfferingResponse(BackupOffering policy);

    ManagementServerResponse createManagementResponse(ManagementServerHost mgmt);

    List<RouterHealthCheckResultResponse> createHealthCheckResponse(VirtualMachine router, List<RouterHealthCheckResult> healthCheckResults);

    RollingMaintenanceResponse createRollingMaintenanceResponse(Boolean success, String details, List<RollingMaintenanceManager.HostUpdated> hostsUpdated, List<RollingMaintenanceManager.HostSkipped> hostsSkipped);

    ResourceIconResponse createResourceIconResponse(ResourceIcon resourceIcon);

    GuestVlanResponse createGuestVlanResponse(GuestVlan vlan);

    NetworkPermissionsResponse createNetworkPermissionsResponse(NetworkPermission permission);

    DirectDownloadCertificateResponse createDirectDownloadCertificateResponse(DirectDownloadCertificate certificate);

    List<DirectDownloadCertificateHostStatusResponse> createDirectDownloadCertificateHostMapResponse(List<DirectDownloadCertificateHostMap> hostMappings);

    DirectDownloadCertificateHostStatusResponse createDirectDownloadCertificateHostStatusResponse(DirectDownloadManager.HostCertificateStatus status);

    DirectDownloadCertificateHostStatusResponse createDirectDownloadCertificateProvisionResponse(Long certificateId, Long hostId, Pair<Boolean, String> result);

    FirewallResponse createIpv6FirewallRuleResponse(FirewallRule acl);

    UnmanagedInstanceResponse createUnmanagedInstanceResponse(UnmanagedInstanceTO instance, Cluster cluster, Host host);

    SecondaryStorageHeuristicsResponse createSecondaryStorageSelectorResponse(Heuristic heuristic);

    IpQuarantineResponse createQuarantinedIpsResponse(PublicIpQuarantine publicIp);

    ObjectStoreResponse createObjectStoreResponse(ObjectStore os);

    BucketResponse createBucketResponse(Bucket bucket);

    DNSZoneResponse createDNSZoneResponse(Zone zone);

    ASNRangeResponse createASNumberRangeResponse(ASNumberRange asnRange);

    ASNumberResponse createASNumberResponse(ASNumber asn);

    BackupRepositoryResponse createBackupRepositoryResponse(BackupRepository repository);

    SharedFSResponse createSharedFSResponse(ResponseView view, SharedFS sharedFS);
}
