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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.cloud.utils.net.NetUtils;
import com.googlecode.ipv6.IPv6Address;
import com.googlecode.ipv6.IPv6Network;

/**
 * Carves aligned subnets out of a parent prefix, for both address families. The arithmetic is
 * done in BigInteger so IPv4 and IPv6 share one code path; a /56 split into /64s is 256 steps at
 * most and a /16 into /30s 16384, so a linear first-fit scan is fine.
 */
public final class SubnetCarver {

    private SubnetCarver() {
    }

    public static int family(String cidr) {
        return cidr != null && cidr.contains(":") ? 6 : 4;
    }

    public static int prefixLength(String cidr) {
        return Integer.parseInt(cidr.substring(cidr.indexOf('/') + 1));
    }

    /** The canonical form: the network address followed by the prefix length. */
    public static String normalize(String cidr) {
        if (family(cidr) == 6) {
            return IPv6Network.fromString(cidr).toString();
        }
        return NetUtils.transformCidr(cidr);
    }

    public static boolean isValid(String cidr, int family) {
        if (cidr == null || !cidr.contains("/")) {
            return false;
        }
        try {
            if (family == 6) {
                return family(cidr) == 6 && NetUtils.isValidIp6Cidr(cidr);
            }
            return family(cidr) == 4 && NetUtils.isValidIp4Cidr(cidr);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean overlap(String a, String b) {
        if (family(a) != family(b)) {
            return false;
        }
        BigInteger[] ra = range(a);
        BigInteger[] rb = range(b);
        return ra[0].compareTo(rb[1]) <= 0 && rb[0].compareTo(ra[1]) <= 0;
    }

    public static boolean contains(String parent, String child) {
        if (family(parent) != family(child)) {
            return false;
        }
        BigInteger[] rp = range(parent);
        BigInteger[] rc = range(child);
        return rp[0].compareTo(rc[0]) <= 0 && rc[1].compareTo(rp[1]) <= 0;
    }

    /**
     * The first block of the given prefix length inside the parent that overlaps none of the
     * taken subnets, aligned to its own size, or null when the parent is full.
     */
    public static String carve(String parent, Collection<String> taken, int prefixLength) {
        int family = family(parent);
        int bits = family == 6 ? 128 : 32;
        if (prefixLength < prefixLength(parent) || prefixLength > bits) {
            return null;
        }
        BigInteger[] rp = range(parent);
        BigInteger size = BigInteger.ONE.shiftLeft(bits - prefixLength);
        List<BigInteger[]> used = new ArrayList<>();
        for (String t : taken) {
            if (t != null && family(t) == family) {
                used.add(range(t));
            }
        }
        BigInteger candidate = rp[0];
        while (candidate.add(size).subtract(BigInteger.ONE).compareTo(rp[1]) <= 0) {
            BigInteger candidateEnd = candidate.add(size).subtract(BigInteger.ONE);
            BigInteger blockedUntil = null;
            for (BigInteger[] u : used) {
                if (u[0].compareTo(candidateEnd) <= 0 && candidate.compareTo(u[1]) <= 0) {
                    if (blockedUntil == null || u[1].compareTo(blockedUntil) > 0) {
                        blockedUntil = u[1];
                    }
                }
            }
            if (blockedUntil == null) {
                return toCidr(candidate, prefixLength, family);
            }
            // jump past the blocking subnet and realign to the block size
            BigInteger next = blockedUntil.add(BigInteger.ONE);
            BigInteger remainder = next.mod(size);
            if (remainder.signum() != 0) {
                next = next.add(size.subtract(remainder));
            }
            candidate = next;
        }
        return null;
    }

    /** First and last address of the subnet as integers. */
    public static BigInteger[] range(String cidr) {
        int family = family(cidr);
        int bits = family == 6 ? 128 : 32;
        int len = prefixLength(cidr);
        String address = cidr.substring(0, cidr.indexOf('/'));
        BigInteger start = toInteger(address, family);
        BigInteger mask = BigInteger.ONE.shiftLeft(bits - len).subtract(BigInteger.ONE);
        start = start.andNot(mask);
        return new BigInteger[] {start, start.or(mask)};
    }

    public static BigInteger toInteger(String address, int family) {
        if (family == 6) {
            return IPv6Address.fromString(address).toBigInteger();
        }
        return BigInteger.valueOf(NetUtils.ip2Long(address));
    }

    public static String toAddress(BigInteger value, int family) {
        if (family == 6) {
            return IPv6Address.fromBigInteger(value).toString();
        }
        return NetUtils.long2Ip(value.longValue());
    }

    private static String toCidr(BigInteger start, int prefixLength, int family) {
        return toAddress(start, family) + "/" + prefixLength;
    }
}
