package org.apache.cloudstack.api.command.user.dns;

import com.cloud.exception.*;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.*;
import org.apache.cloudstack.api.command.user.UserCmd;
import org.apache.cloudstack.api.response.*;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.dns.Zone;

@APICommand(name = "createDNSZone", responseObject = DNSZoneResponse.class,
        description = "Creates a bucket in the specified object storage pool. ", responseView = ResponseObject.ResponseView.Restricted,
        entityType = {Zone.class}, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, since = "4.21.0",
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class CreateZoneCmd extends BaseAsyncCreateCmd implements UserCmd {
    private static final String s_name = "creatednszoneresponse";

    @Parameter(name = ApiConstants.ACCOUNT,
            type = CommandType.STRING,
            description = "the account associated with the DNS zone. Must be used with the domainId parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.PROJECT_ID,
            type = CommandType.UUID,
            entityType = ProjectResponse.class,
            description = "the project associated with the DNS zone. Mutually exclusive with account parameter")
    private Long projectId;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "the domain ID associated with the DNS zone. If used with the account parameter"
                    + " returns the DNS zone associated with the account for the specified domain.")
    private Long domainId;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true,description = "the name of the DNS zone")
    private String zoneName;

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getZoneName() {
        return zoneName;
    }

    private Long getProjectId() {
        return projectId;
    }

    @Override
    public void create() throws ResourceAllocationException {
        Zone zone = zoneApiService.allocZone(this);
        if (zone != null) {
            setEntityId(zone.getId());
            setEntityUuid(zone.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create DNS zone");
        }
    }

    @Override
    public String getEventType() {
        return "";
    }

    @Override
    public String getEventDescription() {
        return "";
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        CallContext.current().setEventDetails("Zone Id: " + getEntityUuid());

        Zone zone;
        try {
            zone = zoneApiService.createZone(this);
        } catch (Exception e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
        if (zone != null) {
            DNSZoneResponse response = _responseGenerator.createDNSZoneResponse(zone);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create DNS zone with name: " + getZoneName());
        }
    }

    @Override
    public long getEntityOwnerId() {
        return 0;
    }
}
