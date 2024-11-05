package org.apache.cloudstack.api.response;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponseWithTagInformation;
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.dns.Zone;

import java.util.Date;

@EntityReference(value = Zone.class)
public class DNSZoneResponse extends BaseResponseWithTagInformation implements ControlledViewEntityResponse, ControlledEntityResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "ID of the DNS zone")
    private String id;
    @SerializedName(ApiConstants.NAME)
    @Param(description = "name of the DNS zone")
    private String name;
    @SerializedName(ApiConstants.CREATED)
    @Param(description = "the date the DNS zone was created")
    private Date created;
    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account associated with the DNS zone")
    private String accountName;
    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the project id of the DNS zone")
    private String projectId;
    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the project name of the DNS zone")
    private String projectName;
    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the ID of the domain associated with the DNS zone")
    private String domainId;
    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the domain associated with the DNS zone")
    private String domainName;

    @SerializedName(ApiConstants.DOMAIN_PATH)
    @Param(description = "path of the domain to which the DNS zone belongs")
    private String domainPath;

    @Override
    public String getObjectId() {
        return this.getId();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    @Override
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    @Override
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    @Override
    public void setDomainPath(String domainPath) {
        this.domainPath = domainPath;
    }
}
