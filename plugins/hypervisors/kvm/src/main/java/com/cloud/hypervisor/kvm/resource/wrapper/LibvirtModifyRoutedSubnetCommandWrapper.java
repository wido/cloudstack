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
package com.cloud.hypervisor.kvm.resource.wrapper;

import org.libvirt.Connect;
import org.libvirt.LibvirtException;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.ModifyRoutedSubnetCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles = ModifyRoutedSubnetCommand.class)
public final class LibvirtModifyRoutedSubnetCommandWrapper extends CommandWrapper<ModifyRoutedSubnetCommand, Answer, LibvirtComputingResource> {

    @Override
    public Answer execute(final ModifyRoutedSubnetCommand command, final LibvirtComputingResource libvirtComputingResource) {
        try {
            final Connect conn = libvirtComputingResource.getLibvirtUtilitiesHelper().getConnectionByVmName(command.getVmName());
            final boolean result = libvirtComputingResource.configureRoutedSubnet(conn, command);
            return new Answer(command, result, result ? "" : "failed to apply the routed subnet on the host");
        } catch (final LibvirtException e) {
            logger.debug("Could not apply routed subnet {} on {}: {}", command.getSubnet(), command.getVmName(), e.getLocalizedMessage());
            return new Answer(command, false, e.getLocalizedMessage());
        }
    }
}
