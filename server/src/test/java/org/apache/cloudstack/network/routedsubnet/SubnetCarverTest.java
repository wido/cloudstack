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
package org.apache.cloudstack.network.routedsubnet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class SubnetCarverTest {

    @Test
    public void firstBlockOfAnEmptyParent() {
        Assert.assertEquals("10.0.0.0/29", SubnetCarver.carve("10.0.0.0/24", Collections.emptyList(), 29));
        Assert.assertEquals("2001:db8::/64", SubnetCarver.carve("2001:db8::/56", Collections.emptyList(), 64));
    }

    @Test
    public void skipsTakenBlocksAndRealigns() {
        List<String> taken = Arrays.asList("10.0.0.0/29", "10.0.0.8/30");
        Assert.assertEquals("10.0.0.16/29", SubnetCarver.carve("10.0.0.0/24", taken, 29));
        Assert.assertEquals("10.0.0.12/30", SubnetCarver.carve("10.0.0.0/24", taken, 30));
        List<String> taken6 = Arrays.asList("2001:db8::/64", "2001:db8:0:1::/64");
        Assert.assertEquals("2001:db8:0:2::/64", SubnetCarver.carve("2001:db8::/56", taken6, 64));
    }

    @Test
    public void largerTakenBlockPushesPastItself() {
        List<String> taken = Arrays.asList("10.0.0.0/25");
        Assert.assertEquals("10.0.0.128/30", SubnetCarver.carve("10.0.0.0/24", taken, 30));
    }

    @Test
    public void fullParentGivesNull() {
        List<String> taken = Arrays.asList("10.0.0.0/25", "10.0.0.128/25");
        Assert.assertNull(SubnetCarver.carve("10.0.0.0/24", taken, 30));
        Assert.assertNull(SubnetCarver.carve("10.0.0.0/24", Collections.emptyList(), 23));
    }

    @Test
    public void otherFamilyIsIgnored() {
        Assert.assertEquals("10.0.0.0/30", SubnetCarver.carve("10.0.0.0/24", Arrays.asList("2001:db8::/64"), 30));
        Assert.assertFalse(SubnetCarver.overlap("10.0.0.0/24", "2001:db8::/64"));
    }

    @Test
    public void overlapAndContainment() {
        Assert.assertTrue(SubnetCarver.overlap("10.0.0.0/24", "10.0.0.128/25"));
        Assert.assertFalse(SubnetCarver.overlap("10.0.0.0/25", "10.0.0.128/25"));
        Assert.assertTrue(SubnetCarver.contains("2001:db8::/56", "2001:db8:0:ff::/64"));
        Assert.assertFalse(SubnetCarver.contains("2001:db8::/56", "2001:db8:1::/64"));
        Assert.assertEquals("10.0.0.0/29", SubnetCarver.normalize("10.0.0.5/29"));
        Assert.assertEquals("2001:db8::/64", SubnetCarver.normalize("2001:db8::1/64"));
    }
}
