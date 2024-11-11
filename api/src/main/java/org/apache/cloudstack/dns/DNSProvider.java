package org.apache.cloudstack.dns;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

public interface DNSProvider extends Configurable, Identity, InternalIdentity {


    ConfigKey<Boolean> DNSProviderEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "network.dns.provider.enabled",
            "false",
            "Is DNS Provider framework enabled.", false, ConfigKey.Scope.Zone);

    ConfigKey<String> DNSProviderDriver = new ConfigKey<String>("Advanced", String.class,
            "network.dns.provider.driver"
            "dummy",
            "This parameter accepts a String and define which driver should be loaded as DNS provider. Only one provider can be used for a CloudStack deployment",
            true, ConfigKey.Scope.Zone, DNSProviderEnabled);

    String getProviderName();
}
