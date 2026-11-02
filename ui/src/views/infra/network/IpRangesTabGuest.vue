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
  <a-spin :spinning="componentLoading">
    <a-button
      :disabled="!('createGuestNetworkIpv6Prefix' in $store.getters.apis)"
      type="dashed"
      style="margin-bottom: 20px; width: 100%"
      @click="handleOpenAddIpv6PrefixForm()">
      <template #icon><plus-outlined /></template>
      {{ $t('label.add.ip.v6.prefix') }}
    </a-button>
    <a-table
      style="overflow-y: auto"
      size="small"
      :columns="ipv6Columns"
      :dataSource="ipv6Prefixes"
      :rowKey="record => record.id + record.prefix"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'allocated'">
          {{ record.usedsubnets + '/' + record.totalsubnets }}
        </template>
        <template v-if="column.key === 'actions'">
          <div class="actions">
            <tooltip-button
              v-if="!record.domainid"
              tooltipPlacement="bottom"
              :tooltip="$t('label.dedicate.ip.v6.prefix')"
              icon="user-add-outlined"
              @click="openDedicateIpv6Prefix(record)"
              :disabled="!('dedicateGuestNetworkIpv6Prefix' in $store.getters.apis)" />
            <tooltip-button
              v-if="record.domainid"
              tooltipPlacement="bottom"
              :tooltip="$t('label.release.dedicated.ip.v6.prefix')"
              icon="user-delete-outlined"
              type="primary"
              :danger="true"
              @click="releaseDedicatedIpv6Prefix(record)"
              :disabled="!('releaseDedicatedGuestNetworkIpv6Prefix' in $store.getters.apis)" />
            <tooltip-button
              tooltipPlacement="bottom"
              :tooltip="$t('label.delete.ip.v6.prefix')"
              type="primary"
              icon="delete-outlined"
              :danger="true"
              @click="handleDeleteIpv6Prefix(record)"
              :disabled="!('deleteGuestNetworkIpv6Prefix' in $store.getters.apis)" />
          </div>
        </template>
      </template>
    </a-table>
    <br>
    <br>

    <a-button
      :disabled="!('createNetwork' in $store.getters.apis)"
      type="dashed"
      style="margin-bottom: 20px; width: 100%"
      @click="handleOpenShowCreateForm">
      <template #icon><plus-outlined /></template>
      {{ $t('label.add.guest.network') }}
    </a-button>

    <a-table
      size="small"
      style="overflow-y: auto"
      :columns="columns"
      :dataSource="items"
      :rowKey="record => record.id"
      :pagination="false"
    >
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.key === 'name'">
          <resource-icon v-if="record.icon" :image="record.icon.base64image" size="1x" style="margin-right: 5px"/>
          <apartment-outlined v-else style="margin-right: 5px"/>
          <router-link :to="{ path: '/guestnetwork/' + record.id }">
            {{ text }}
          </router-link>
        </template>
      </template>
    </a-table>
    <a-pagination
      class="row-element pagination"
      size="small"
      :current="page"
      :pageSize="pageSize"
      :total="total"
      :showTotal="total => `${$t('label.total')} ${total} ${$t('label.items')}`"
      :pageSizeOptions="['10', '20', '40', '80', '100']"
      @change="changePage"
      @showSizeChange="changePageSize"
      showSizeChanger>
      <template #buildOptionText="props">
        <span>{{ props.value }} / {{ $t('label.page') }}</span>
      </template>
    </a-pagination>

    <a-modal
      v-if="showCreateForm"
      :visible="showCreateForm"
      :title="$t('label.add.guest.network')"
      :closable="true"
      :maskClosable="false"
      :footer="null"
      @cancel="showCreateForm = false"
      centered
      width="auto">
      <CreateNetwork :resource="{ zoneid: resource.zoneid }" @close-action="closeAction"/>
    </a-modal>

    <a-modal
      :visible="addIpv6PrefixModal"
      :title="$t('Add IPv6 Prefix')"
      :maskClosable="false"
      :footer="null"
      @cancel="handleCloseAddIpv6PrefixForm()"
      centered
      v-ctrl-enter="handleAddIpv6Prefix">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        @submit="handleAddIpv6Prefix"
        layout="vertical"
        class="form"
      >
        <a-form-item name="prefix" ref="prefix" :label="$t('label.prefix')" class="form__item">
          <a-input v-model:value="form.prefix" />
        </a-form-item>
        <a-form-item name="purpose" ref="purpose" :label="$t('label.purpose')" class="form__item">
          <a-select v-model:value="form.purpose">
            <a-select-option value="Network">{{ $t('label.purpose.network') }}</a-select-option>
            <a-select-option value="Instance">{{ $t('label.purpose.instance') }}</a-select-option>
          </a-select>
        </a-form-item>
        <div class="form__item">
          <div style="color: black;">{{ $t('label.set.reservation') }}</div>
          <a-switch v-model:checked="showDedicationFields" />
        </div>
        <div v-if="showDedicationFields" style="margin-top: 20px;">
          <div v-html="$t('message.ipv6.prefix.set.reservation.desc')"></div><br>
          <a-form-item name="account" ref="account" :label="$t('label.account')" class="form__item">
            <a-input v-model:value="form.account"></a-input>
          </a-form-item>
          <a-form-item name="domainid" ref="domainid" :label="$t('label.domain')" class="form__item">
            <a-select
              v-model:value="form.domainid"
              :loading="domainsLoading"
              showSearch
              optionFilterProp="label"
              :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
              <a-select-option v-for="domain in domains" :key="domain.id" :value="domain.id" :label="domain.path || domain.name">{{ domain.path || domain.name }}</a-select-option>
            </a-select>
          </a-form-item>
        </div>

        <div :span="24" class="action-button">
          <a-button @click="handleCloseAddIpv6PrefixForm()">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" ref="submit" @click="handleAddIpv6Prefix">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      v-if="dedicateIpv6PrefixModal"
      :visible="dedicateIpv6PrefixModal"
      :title="$t('label.dedicate.ip.v6.prefix')"
      :closable="true"
      :maskClosable="false"
      :footer="null"
      @cancel="dedicateIpv6PrefixModal = false">
      <a-spin :spinning="domainsLoading" v-ctrl-enter="handleDedicateIpv6Prefix">
        <div style="margin-bottom: 10px;">
          <div class="list__label">{{ $t('label.account') }}:</div>
          <a-input v-model:value="dedicateForm.account" v-focus="true"></a-input>
        </div>
        <div>
          <div class="list__label">{{ $t('label.domain') }}:</div>
          <a-select
            v-model:value="dedicateForm.domainid"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
            <a-select-option v-for="domain in domains" :key="domain.id" :value="domain.id" :label="domain.path || domain.name">{{ domain.path || domain.name }}</a-select-option>
          </a-select>
        </div>
        <div :span="24" class="action-button">
          <a-button @click="dedicateIpv6PrefixModal = false">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" ref="submit" @click="handleDedicateIpv6Prefix">{{ $t('label.ok') }}</a-button>
        </div>
      </a-spin>
    </a-modal>

  </a-spin>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import CreateNetwork from '@/views/network/CreateNetwork'
import ResourceIcon from '@/components/view/ResourceIcon'
import TooltipButton from '@/components/widgets/TooltipButton'

export default {
  name: 'IpRangesTabGuest',
  components: {
    CreateNetwork,
    ResourceIcon,
    TooltipButton
  },
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
      componentLoading: false,
      items: [],
      total: 0,
      showCreateForm: false,
      page: 1,
      pageSize: 10,
      columns: [
        {
          key: 'name',
          title: this.$t('label.name'),
          dataIndex: 'name'
        },
        {
          title: this.$t('label.type'),
          dataIndex: 'type'
        },
        {
          title: this.$t('label.vlan'),
          dataIndex: 'vlan'
        },
        {
          title: this.$t('label.broadcasturi'),
          dataIndex: 'broadcasturi'
        },
        {
          title: this.$t('label.cidr'),
          dataIndex: 'cidr'
        },
        {
          title: this.$t('label.ip6cidr'),
          dataIndex: 'ip6cidr'
        }
      ],
      ipv6Prefixes: [],
      ipv6Columns: [
        {
          title: this.$t('label.prefix'),
          dataIndex: 'prefix'
        },
        {
          title: this.$t('label.purpose'),
          dataIndex: 'purpose'
        },
        {
          title: this.$t('label.domain'),
          dataIndex: 'domain'
        },
        {
          title: this.$t('label.account'),
          dataIndex: 'account'
        },
        {
          key: 'allocated',
          title: this.$t('label.allocated')
        },
        {
          key: 'actions',
          title: this.$t('label.actions')
        }
      ],
      addIpv6PrefixModal: false,
      dedicateIpv6PrefixModal: false,
      showDedicationFields: false,
      domainsLoading: false,
      domains: [],
      dedicateForm: { account: '', domainid: undefined },
      dedicatePrefix: null
    }
  },
  beforeCreate () {
    this.form = null
    this.formRef = null
    this.rules = null
  },
  created () {
    this.fetchData()
  },
  watch: {
    resource (newItem, oldItem) {
      if (!newItem || !newItem.id) {
        return
      }
      this.fetchData()
    }
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({
        prefix: [{ required: true, message: this.$t('label.required') }]
      })
    },
    fetchData () {
      this.componentLoading = true
      getAPI('listNetworks', {
        zoneid: this.resource.zoneid,
        physicalnetworkid: this.resource.id,
        showicon: true,
        page: this.page,
        pagesize: this.pageSize
      }).then(response => {
        this.items = response?.listnetworksresponse?.network || []
        this.total = response?.listnetworksresponse?.count || 0
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.componentLoading = false
      })
      this.fetchIpv6PrefixData()
    },
    fetchIpv6PrefixData () {
      getAPI('listGuestNetworkIpv6Prefixes', {
        zoneid: this.resource.zoneid,
        page: this.page,
        pagesize: this.pageSize
      }).then(response => {
        this.ipv6Prefixes = response?.listguestnetworkipv6prefixesresponse?.guestnetworkipv6prefix || []
        this.total = response?.listguestnetworkipv6prefixesresponse?.count || 0
      }).catch(error => {
        console.log(error)
        this.$notifyError(error)
      }).finally(() => {
      })
    },
    handleOpenShowCreateForm () {
      this.showCreateForm = true
    },
    closeAction () {
      this.showCreateForm = false
    },
    handleOpenAddIpv6PrefixForm () {
      this.initForm()
      this.fetchDomains()
      this.addIpv6PrefixModal = true
    },
    handleCloseAddIpv6PrefixForm () {
      this.formRef.value.resetFields()
      this.addIpv6PrefixModal = false
    },
    changePage (page, pageSize) {
      this.page = page
      this.pageSize = pageSize
      this.fetchData()
    },
    changePageSize (currentPage, pageSize) {
      this.page = currentPage
      this.pageSize = pageSize
      this.fetchData()
    },
    handleAddIpv6Prefix (e) {
      if (this.componentLoading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)

        this.componentLoading = true
        this.addIpv6PrefixModal = false
        var params = {
          zoneid: this.resource.zoneid,
          prefix: values.prefix,
          purpose: values.purpose || 'Network'
        }
        if (this.showDedicationFields) {
          if (values.domainid) {
            params.domainid = values.domainid
          }
          if (values.account) {
            params.account = values.account
          }
        }
        this.showDedicationFields = false
        postAPI('createGuestNetworkIpv6Prefix', params).then(response => {
          this.$pollJob({
            jobId: response.createguestnetworkipv6prefixresponse.jobid,
            title: this.$t('label.add.ip.v6.prefix'),
            description: values.prefix,
            successMessage: this.$t('message.success.add.ip.v6.prefix'),
            successMethod: () => {
              this.componentLoading = false
              this.fetchData()
            },
            errorMessage: this.$t('message.add.failed'),
            errorMethod: () => {
              this.componentLoading = false
              this.fetchData()
            },
            loadingMessage: this.$t('message.add.ip.v6.prefix.processing'),
            catchMessage: this.$t('error.fetching.async.job.result'),
            catchMethod: () => {
              this.componentLoading = false
              this.fetchData()
            }
          })
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.componentLoading = false
          this.fetchData()
        })
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    fetchDomains () {
      if (this.domains.length > 0) {
        return
      }
      this.domainsLoading = true
      getAPI('listDomains', { listAll: true, details: 'min' }).then(response => {
        this.domains = response.listdomainsresponse.domain || []
        if (this.domains.length > 0 && !this.form.domainid) {
          this.form.domainid = this.domains[0].id
          this.dedicateForm.domainid = this.domains[0].id
        }
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.domainsLoading = false
      })
    },
    openDedicateIpv6Prefix (prefix) {
      this.dedicatePrefix = prefix
      this.dedicateForm = { account: '', domainid: this.domains.length > 0 ? this.domains[0].id : undefined }
      this.fetchDomains()
      this.dedicateIpv6PrefixModal = true
    },
    handleDedicateIpv6Prefix () {
      if (this.componentLoading || !this.dedicatePrefix) return
      const params = { id: this.dedicatePrefix.id, domainid: this.dedicateForm.domainid }
      if (this.dedicateForm.account) {
        params.account = this.dedicateForm.account
      }
      this.dedicateIpv6PrefixModal = false
      this.componentLoading = true
      postAPI('dedicateGuestNetworkIpv6Prefix', params).then(response => {
        this.$pollJob({
          jobId: response.dedicateguestnetworkipv6prefixresponse.jobid,
          title: this.$t('label.dedicate.ip.v6.prefix'),
          description: this.dedicatePrefix.prefix,
          successMessage: this.$t('message.success.dedicate.ip.v6.prefix'),
          successMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          },
          errorMessage: this.$t('label.error'),
          errorMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          },
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.componentLoading = false
      })
    },
    releaseDedicatedIpv6Prefix (prefix) {
      this.componentLoading = true
      postAPI('releaseDedicatedGuestNetworkIpv6Prefix', { id: prefix.id }).then(response => {
        this.$pollJob({
          jobId: response.releasededicatedguestnetworkipv6prefixresponse.jobid,
          title: this.$t('label.release.dedicated.ip.v6.prefix'),
          description: prefix.prefix,
          successMessage: this.$t('message.success.release.dedicated.ip.v6.prefix'),
          successMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          },
          errorMessage: this.$t('label.error'),
          errorMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          },
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.componentLoading = false
      })
    },
    handleDeleteIpv6Prefix (prefix) {
      this.componentLoading = true
      postAPI('deleteGuestNetworkIpv6Prefix', { id: prefix.id }).then(response => {
        this.$pollJob({
          jobId: response.deleteguestnetworkipv6prefixresponse.jobid,
          title: this.$t('label.delete.ip.v6.prefix'),
          description: prefix.prefix,
          successMessage: this.$t('message.ip.v6.prefix.deleted') + ' ' + prefix.prefix,
          successMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          },
          errorMessage: this.$t('message.delete.failed'),
          errorMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          },
          loadingMessage: this.$t('message.delete.ip.v6.prefix.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.componentLoading = false
            this.fetchIpv6PrefixData()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.componentLoading = false
        this.fetchIpv6PrefixData()
      })
    }
  }
}
</script>

<style lang="scss" scoped>
  .pagination {
    margin-top: 20px;
  }
</style>
