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
    <p v-if="attachMode">{{ $t('message.attach.routed.subnet') }}</p>
    <p v-else>{{ $t('message.assign.routed.subnet.desc') }}</p>
    <a-form
      class="form"
      layout="vertical"
      :ref="formRef"
      :model="form"
      :rules="rules"
      @finish="handleSubmit"
      v-ctrl-enter="handleSubmit">
      <a-form-item name="virtualmachineid" ref="virtualmachineid" v-if="!nicId">
        <template #label>
          <tooltip-label :title="$t('label.instance')"/>
        </template>
        <a-select
          v-model:value="form.virtualmachineid"
          :loading="vmsLoading"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0"
          @change="selectVm">
          <a-select-option v-for="vm in vms" :key="vm.id" :value="vm.id" :label="vm.name || vm.displayname">{{ vm.name || vm.displayname }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item name="nicid" ref="nicid" v-if="!nicId">
        <template #label>
          <tooltip-label :title="$t('label.nic')" :tooltip="apiParams.nicid.description"/>
        </template>
        <a-select
          v-model:value="form.nicid"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
          <a-select-option v-for="nic in nics" :key="nic.id" :value="nic.id" :label="nicLabel(nic)">{{ nicLabel(nic) }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item name="offeringid" ref="offeringid" v-if="!attachMode">
        <template #label>
          <tooltip-label :title="$t('label.routed.subnet.offering')" :tooltip="apiParams.offeringid.description"/>
        </template>
        <a-select
          v-model:value="form.offeringid"
          :loading="offeringsLoading"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
          <a-select-option v-for="offering in offerings" :key="offering.id" :value="offering.id" :label="offeringLabel(offering)">{{ offeringLabel(offering) }}</a-select-option>
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
  name: 'AssignRoutedSubnet',
  mixins: [mixinForm],
  components: {
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      default: () => {}
    },
    nicId: {
      type: String,
      default: null
    },
    zoneId: {
      type: String,
      default: null
    }
  },
  data () {
    return {
      loading: false,
      vmsLoading: false,
      offeringsLoading: false,
      vms: [],
      nics: [],
      offerings: []
    }
  },
  computed: {
    attachMode () {
      return this.resource && this.resource.subnet && this.resource.mode === 'BGP'
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('assignRoutedSubnet')
  },
  created () {
    this.initForm()
    this.fetchData()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({
        nicid: [{ required: !this.nicId, message: this.$t('message.error.required.input') }],
        offeringid: [{ required: !this.attachMode, message: this.$t('message.error.required.input') }]
      })
    },
    fetchData () {
      if (!this.nicId) {
        this.fetchVms()
      }
      if (!this.attachMode) {
        this.fetchOfferings()
      }
    },
    fetchVms () {
      this.vmsLoading = true
      const params = { listall: true, details: 'nics', pagesize: 500 }
      if (this.zoneId) {
        params.zoneid = this.zoneId
      } else if (this.attachMode && this.resource.zoneid) {
        params.zoneid = this.resource.zoneid
      }
      getAPI('listVirtualMachines', params).then(json => {
        this.vms = (json.listvirtualmachinesresponse.virtualmachine || []).filter(vm => (vm.nic || []).some(this.nicSupported))
      }).finally(() => {
        this.vmsLoading = false
      })
    },
    nicSupported (nic) {
      return nic.type === 'L3' || nic.type === 'Shared'
    },
    selectVm (vmId) {
      const vm = this.vms.find(v => v.id === vmId)
      this.nics = vm ? (vm.nic || []).filter(this.nicSupported) : []
      this.form.nicid = this.nics.length > 0 ? this.nics[0].id : undefined
    },
    nicLabel (nic) {
      return [nic.networkname, nic.ipaddress, nic.ip6address].filter(x => x).join(' ')
    },
    fetchOfferings () {
      this.offeringsLoading = true
      const params = { listall: true, state: 'Active' }
      getAPI('listRoutedSubnetOfferings', params).then(json => {
        this.offerings = json.listroutedsubnetofferingsresponse.routedsubnetoffering || []
      }).finally(() => {
        this.offeringsLoading = false
      })
    },
    offeringLabel (offering) {
      const sizes = []
      if (offering.ip4prefixlength) sizes.push('IPv4 /' + offering.ip4prefixlength)
      if (offering.ip6prefixlength) sizes.push('IPv6 /' + offering.ip6prefixlength)
      return offering.displaytext + ' (' + offering.mode + ', ' + sizes.join(', ') + ')'
    },
    handleSubmit (e) {
      e.preventDefault()
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        const params = { nicid: this.nicId || values.nicid }
        if (this.attachMode) {
          params.subnetid = this.resource.id
        } else {
          params.offeringid = values.offeringid
        }
        this.loading = true
        postAPI('assignRoutedSubnet', params).then(response => {
          this.$pollJob({
            jobId: response.assignroutedsubnetresponse.jobid,
            title: this.$t('label.assign.routed.subnet'),
            successMessage: this.$t('message.success.assign.routed.subnet'),
            successMethod: () => {
              this.$emit('refresh-data')
            },
            errorMessage: this.$t('label.error'),
            loadingMessage: this.$t('label.assign.routed.subnet'),
            catchMessage: this.$t('error.fetching.async.job.result')
          })
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
