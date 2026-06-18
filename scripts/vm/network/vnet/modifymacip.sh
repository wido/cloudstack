#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# modifymacip.sh -- Manage static ARP/NDP entries and host routes for VM NICs
#
# Usage:
#   add:    modifymacip.sh -o add    -b <bridge> -m <mac> [-4 <ipv4>] [-6 <ipv6>]
#   delete: modifymacip.sh -o delete -b <bridge> -m <mac>
#
# On add the script persists the NIC's address info so that delete can
# recover the IPs without them being passed explicitly.

STATE_DIR=/var/run/cloud/macip

usage() {
    echo "Usage: $0 -o <add|delete> -b <bridge> -m <mac> [-4 <ipv4>] [-6 <ipv6>]"
}

OP=
BRIDGE=
MAC=
IPV4=
IPV6=

while getopts 'o:b:m:4:6:' OPTION; do
    case $OPTION in
    o) OP="$OPTARG" ;;
    b) BRIDGE="$OPTARG" ;;
    m) MAC="$OPTARG" ;;
    4) IPV4="$OPTARG" ;;
    6) IPV6="$OPTARG" ;;
    ?) usage; exit 2 ;;
    esac
done

if [[ -z "$OP" || -z "$BRIDGE" || -z "$MAC" ]]; then
    usage
    exit 2
fi

# Normalise MAC address for use as a filename (replace colons with underscores)
MAC_NORM="${MAC//:/_}"
STATE_FILE="${STATE_DIR}/${MAC_NORM}.conf"

add_entries() {
    mkdir -p "${STATE_DIR}"

    # Persist state so that delete can recover IPs without them being passed in
    printf 'IPV4=%s\nIPV6=%s\nBRIDGE=%s\n' "${IPV4}" "${IPV6}" "${BRIDGE}" > "${STATE_FILE}"

    if [[ -n "$IPV4" ]]; then
        ip neigh replace "${IPV4}" lladdr "${MAC}" dev "${BRIDGE}" nud permanent
        ip route replace "${IPV4}/32" dev "${BRIDGE}"
    fi

    if [[ -n "$IPV6" ]]; then
        ip -6 neigh replace "${IPV6}" lladdr "${MAC}" dev "${BRIDGE}" nud permanent
        ip -6 route replace "${IPV6}/128" dev "${BRIDGE}"
    fi
}

delete_entries() {
    local del_ipv4="${IPV4}"
    local del_ipv6="${IPV6}"
    local del_bridge="${BRIDGE}"

    # Recover IPs and bridge from the state file written at add time
    if [[ -f "${STATE_FILE}" ]]; then
        while IFS='=' read -r key value; do
            case "$key" in
                IPV4)   del_ipv4="${del_ipv4:-$value}" ;;
                IPV6)   del_ipv6="${del_ipv6:-$value}" ;;
                BRIDGE) del_bridge="${del_bridge:-$value}" ;;
            esac
        done < "${STATE_FILE}"
    fi

    if [[ -n "$del_ipv4" ]]; then
        ip neigh del "${del_ipv4}" dev "${del_bridge}" 2>/dev/null || true
        ip route del "${del_ipv4}/32" dev "${del_bridge}" 2>/dev/null || true
    fi

    if [[ -n "$del_ipv6" ]]; then
        ip -6 neigh del "${del_ipv6}" dev "${del_bridge}" 2>/dev/null || true
        ip -6 route del "${del_ipv6}/128" dev "${del_bridge}" 2>/dev/null || true
    fi

    rm -f "${STATE_FILE}"
}

case "$OP" in
    add)    add_entries ;;
    delete) delete_entries ;;
    *)      usage; exit 2 ;;
esac
