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
    <p>{{ $t('message.change.routed.subnet.offering.desc') }}</p>
    <a-form
      class="form"
      layout="vertical"
      :ref="formRef"
      :model="form"
      :rules="rules"
      @finish="handleSubmit"
      v-ctrl-enter="handleSubmit">
      <a-form-item name="offeringid" ref="offeringid">
        <template #label>
          <tooltip-label :title="$t('label.routed.subnet.offering')" :tooltip="apiParams.offeringid.description"/>
        </template>
        <a-select
          v-model:value="form.offeringid"
          :loading="offeringsLoading"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
          <a-select-option v-for="offering in offerings" :key="offering.id" :value="offering.id" :label="offering.displaytext + ' (' + offering.mode + ')'">{{ offering.displaytext }} ({{ offering.mode }})</a-select-option>
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
  name: 'ChangeRoutedSubnetOffering',
  mixins: [mixinForm],
  components: {
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      loading: false,
      offeringsLoading: false,
      offerings: []
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('updateRoutedSubnet')
  },
  created () {
    this.initForm()
    this.fetchOfferings()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({
        offeringid: [{ required: true, message: this.$t('message.error.required.input') }]
      })
    },
    fetchOfferings () {
      this.offeringsLoading = true
      const length = parseInt(this.resource.subnet.split('/')[1])
      const family = this.resource.addressfamily
      getAPI('listRoutedSubnetOfferings', { listall: true, state: 'Active' }).then(json => {
        const all = json.listroutedsubnetofferingsresponse.routedsubnetoffering || []
        this.offerings = all.filter(o => o.id !== this.resource.offeringid &&
          (family === 4 ? o.ip4prefixlength === length : o.ip6prefixlength === length))
      }).finally(() => {
        this.offeringsLoading = false
      })
    },
    handleSubmit (e) {
      e.preventDefault()
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        this.loading = true
        postAPI('updateRoutedSubnet', { id: this.resource.id, offeringid: values.offeringid }).then(response => {
          this.$pollJob({
            jobId: response.updateroutedsubnetresponse.jobid,
            title: this.$t('label.change.routed.subnet.offering'),
            successMessage: this.$t('message.success.change.routed.subnet.offering'),
            successMethod: () => {
              this.$emit('refresh-data')
            },
            errorMessage: this.$t('label.error'),
            loadingMessage: this.$t('label.change.routed.subnet.offering'),
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
