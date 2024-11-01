package org.apache.cloudstack.dns;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

public interface DNSProvider extends Identity, InternalIdentity {
    String getProviderName();
}
