package org.apache.cloudstack.dns;

import com.cloud.exception.ResourceAllocationException;
import com.cloud.utils.component.ManagerBase;
import org.apache.cloudstack.api.command.user.dns.CreateZoneCmd;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

public class ZoneApiServiceImpl extends ManagerBase implements ZoneApiService, Configurable {
    @Override
    public Zone createZone(CreateZoneCmd cmd) {
        return null;
    }

    @Override
    public Zone allocZone(CreateZoneCmd cmd) throws ResourceAllocationException {
        return null;
    }

    @Override
    public String getConfigComponentName() {
        return "";
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey[0];
    }
}
