// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
<template>
  <a-spin :spinning="loading">
    <h3>{{ $t('label.nics') }}</h3>
    <a-table
      size="small"
      :columns="nicColumns"
      :dataSource="resource.nics || []"
      :rowKey="record => record.id"
      :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'virtualmachinename'">
          <router-link :to="{ path: '/vm/' + record.virtualmachineid }">{{ record.virtualmachinename }}</router-link>
        </template>
        <template v-if="column.key === 'networkid'">
          <router-link :to="{ path: '/guestnetwork/' + record.networkid }">{{ record.networkid }}</router-link>
        </template>
      </template>
    </a-table>
    <div v-if="resource.mode === 'Static'">
      <br>
      <h3>{{ $t('label.endpoints') }}</h3>
      <a-table
        size="small"
        :columns="endpointColumns"
        :dataSource="resource.endpoints || []"
        :rowKey="record => record.endpoint"
        :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'announced'">
            <a-tag v-if="record.announced" color="green">{{ $toLocaleDate(record.announced) }}</a-tag>
            <a-tag v-else color="red">{{ $t('label.no') }}</a-tag>
          </template>
          <template v-if="column.key === 'lastattempt'">
            {{ record.lastattempt ? $toLocaleDate(record.lastattempt) : '' }}
          </template>
        </template>
      </a-table>
    </div>
  </a-spin>
</template>

<script>
export default {
  name: 'RoutedSubnetTab',
  props: {
    resource: {
      type: Object,
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      nicColumns: [
        { key: 'virtualmachinename', title: this.$t('label.instance'), dataIndex: 'virtualmachinename' },
        { key: 'nexthop', title: this.$t('label.nexthop'), dataIndex: 'nexthop' },
        { key: 'networkid', title: this.$t('label.network'), dataIndex: 'networkid' },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state' },
        { key: 'announced', title: this.$t('label.announced'), dataIndex: 'announced' }
      ],
      endpointColumns: [
        { key: 'endpoint', title: this.$t('label.endpoint'), dataIndex: 'endpoint' },
        { key: 'announced', title: this.$t('label.announced'), dataIndex: 'announced' },
        { key: 'lastattempt', title: this.$t('label.lastattempt'), dataIndex: 'lastattempt' },
        { key: 'lasterror', title: this.$t('label.lasterror'), dataIndex: 'lasterror' }
      ]
    }
  }
}
</script>
