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
    <a-form
      class="form"
      layout="vertical"
      :ref="formRef"
      :model="form"
      :rules="rules"
      @finish="handleSubmit"
      v-ctrl-enter="handleSubmit">
      <a-form-item name="name" ref="name">
        <template #label>
          <tooltip-label :title="$t('label.name')" :tooltip="apiParams.name.description"/>
        </template>
        <a-input v-model:value="form.name" v-focus="true" />
      </a-form-item>
      <a-form-item name="displaytext" ref="displaytext">
        <template #label>
          <tooltip-label :title="$t('label.displaytext')" :tooltip="apiParams.displaytext.description"/>
        </template>
        <a-input v-model:value="form.displaytext" />
      </a-form-item>
      <a-form-item name="mode" ref="mode">
        <template #label>
          <tooltip-label :title="$t('label.mode')" :tooltip="apiParams.mode.description"/>
        </template>
        <a-radio-group v-model:value="form.mode" button-style="solid">
          <a-radio-button value="Static">{{ $t('label.routed.subnet.mode.static') }}</a-radio-button>
          <a-radio-button value="BGP">{{ $t('label.routed.subnet.mode.bgp') }}</a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item name="ip4prefixlength" ref="ip4prefixlength">
        <template #label>
          <tooltip-label :title="$t('label.ip4prefixlength')" :tooltip="apiParams.ip4prefixlength.description"/>
        </template>
        <a-input-number v-model:value="form.ip4prefixlength" :min="8" :max="30" style="width: 100%" />
      </a-form-item>
      <a-form-item name="ip6prefixlength" ref="ip6prefixlength">
        <template #label>
          <tooltip-label :title="$t('label.ip6prefixlength')" :tooltip="apiParams.ip6prefixlength.description"/>
        </template>
        <a-input-number v-model:value="form.ip6prefixlength" :min="32" :max="64" style="width: 100%" />
      </a-form-item>
      <a-form-item name="zoneid" ref="zoneid">
        <template #label>
          <tooltip-label :title="$t('label.zone')" :tooltip="apiParams.zoneid.description"/>
        </template>
        <a-select
          v-model:value="form.zoneid"
          allowClear
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0"
          @change="fetchBgpPeers">
          <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id" :label="zone.name">{{ zone.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item name="domainid" ref="domainid">
        <template #label>
          <tooltip-label :title="$t('label.domain')" :tooltip="apiParams.domainid.description"/>
        </template>
        <a-select
          v-model:value="form.domainid"
          allowClear
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
          <a-select-option v-for="domain in domains" :key="domain.id" :value="domain.id" :label="domain.path || domain.name">{{ domain.path || domain.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item name="bgppeers" ref="bgppeers" v-if="form.mode === 'BGP'">
        <template #label>
          <tooltip-label :title="$t('label.bgp.peers')" :tooltip="$t('label.bgp.peers.restriction')"/>
        </template>
        <a-select
          v-model:value="form.bgppeers"
          mode="multiple"
          :loading="peersLoading"
          optionFilterProp="label"
          :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
          <a-select-option v-for="peer in bgpPeers" :key="peer.id" :value="peer.id" :label="peerLabel(peer)">{{ peerLabel(peer) }}</a-select-option>
        </a-select>
      </a-form-item>
      <div :span="24" class="action-button">
        <a-button @click="closeModal">{{ $t('label.cancel') }}</a-button>
        <a-button type="primary" ref="submit" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
      </div>
    </a-form>
  </a-spin>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import { mixinForm } from '@/utils/mixin'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'AddRoutedSubnetOffering',
  mixins: [mixinForm],
  components: {
    TooltipLabel
  },
  data () {
    return {
      loading: false,
      peersLoading: false,
      zones: [],
      domains: [],
      bgpPeers: []
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('createRoutedSubnetOffering')
  },
  created () {
    this.initForm()
    this.fetchData()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({ mode: 'Static', bgppeers: [] })
      this.rules = reactive({
        name: [{ required: true, message: this.$t('message.error.required.input') }],
        mode: [{ required: true, message: this.$t('message.error.required.input') }]
      })
    },
    fetchData () {
      this.loading = true
      getAPI('listZones', { listall: true }).then(json => {
        this.zones = json.listzonesresponse.zone || []
      }).finally(() => {
        this.loading = false
      })
      getAPI('listDomains', { listall: true }).then(json => {
        this.domains = json.listdomainsresponse.domain || []
      })
      this.fetchBgpPeers()
    },
    fetchBgpPeers () {
      if (!('listBgpPeers' in this.$store.getters.apis)) {
        return
      }
      this.peersLoading = true
      const params = { listall: true }
      if (this.form.zoneid) {
        params.zoneid = this.form.zoneid
      }
      getAPI('listBgpPeers', params).then(json => {
        this.bgpPeers = json.listbgppeersresponse.bgppeer || []
      }).finally(() => {
        this.peersLoading = false
      })
    },
    peerLabel (peer) {
      const address = [peer.ipaddress, peer.ip6address].filter(a => a).join(' / ')
      return address + ' AS' + peer.asnumber + (peer.account ? ' (' + peer.account + ')' : '')
    },
    handleSubmit (e) {
      e.preventDefault()
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        if (!values.ip4prefixlength && !values.ip6prefixlength) {
          this.$message.error(this.$t('label.ip4prefixlength') + ' / ' + this.$t('label.ip6prefixlength'))
          return
        }
        const params = {
          name: values.name,
          displaytext: values.displaytext || values.name,
          mode: values.mode
        }
        if (values.ip4prefixlength) {
          params.ip4prefixlength = values.ip4prefixlength
        }
        if (values.ip6prefixlength) {
          params.ip6prefixlength = values.ip6prefixlength
        }
        if (values.zoneid) {
          params.zoneid = values.zoneid
        }
        if (values.domainid) {
          params.domainid = values.domainid
        }
        if (values.mode === 'BGP' && values.bgppeers && values.bgppeers.length > 0) {
          params['details[0].key'] = 'bgp.peers'
          params['details[0].value'] = values.bgppeers.join(',')
        }
        this.loading = true
        postAPI('createRoutedSubnetOffering', params).then(() => {
          this.$message.success(this.$t('message.success.add.routed.subnet.offering'))
          this.$emit('refresh-data')
          this.closeModal()
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    closeModal () {
      this.$emit('close-action')
    }
  }
}
</script>

<style lang="scss" scoped>
.form {
  width: 80vw;

  @media (min-width: 500px) {
    min-width: 400px;
    width: 100%;
  }
}
</style>
