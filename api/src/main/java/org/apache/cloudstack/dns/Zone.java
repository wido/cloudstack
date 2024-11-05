package org.apache.cloudstack.dns;

import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import java.util.Date;

public interface Zone extends ControlledEntity, Identity, InternalIdentity {
    Date getCreated();

    State getState();

    void setName(final String name);

    public enum State {
        Allocated, Created, Destroyed;
        @Override
        public String toString() {
            return this.name();
        }

        public boolean equals(String status) {
            return this.toString().equalsIgnoreCase(status);
        }
    }
}
