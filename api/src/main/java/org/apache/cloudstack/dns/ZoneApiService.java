package org.apache.cloudstack.dns;

import com.cloud.exception.ResourceAllocationException;
import org.apache.cloudstack.api.command.user.dns.CreateZoneCmd;

public interface ZoneApiService {

    Zone createZone(CreateZoneCmd cmd);

    Zone allocZone(CreateZoneCmd cmd) throws ResourceAllocationException;
}
