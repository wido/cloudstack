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
package com.cloud.agent.api;

/**
 * Adds or removes one subnet routed to an Instance's NIC on a running Instance. On a Direct
 * Routed host the route "subnet via the NIC address" is installed or removed; on every host the
 * security group ipsets are updated when security groups apply to the Instance.
 */
public class ModifyRoutedSubnetCommand extends Command {

    private String vmName;
    private String vmMac;
    private String subnet;
    private String nextHop;
    private long metric;
    private boolean add;
    private boolean directRouted;
    private boolean applySecurityGroupRules;

    protected ModifyRoutedSubnetCommand() {
    }

    public ModifyRoutedSubnetCommand(String vmName, String vmMac, String subnet, String nextHop, long metric, boolean add, boolean directRouted, boolean applySecurityGroupRules) {
        this.vmName = vmName;
        this.vmMac = vmMac;
        this.subnet = subnet;
        this.nextHop = nextHop;
        this.metric = metric;
        this.add = add;
        this.directRouted = directRouted;
        this.applySecurityGroupRules = applySecurityGroupRules;
    }

    public String getVmName() {
        return vmName;
    }

    public String getVmMac() {
        return vmMac;
    }

    public String getSubnet() {
        return subnet;
    }

    public String getNextHop() {
        return nextHop;
    }

    public long getMetric() {
        return metric;
    }

    public boolean isAdd() {
        return add;
    }

    public boolean isDirectRouted() {
        return directRouted;
    }

    public boolean isApplySecurityGroupRules() {
        return applySecurityGroupRules;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
