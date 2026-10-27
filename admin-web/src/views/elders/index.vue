<template>
  <div class="elders-container">
    <PageHeader
      eyebrow="老人档案"
      title="把照护，落实到每一位老人"
      description="管理老人档案与照护需求，核验家属代约关系。"
    />
    <el-card shadow="never" class="main-card">
      <el-tabs :model-value="activeTab" @update:model-value="switchTab">
        <!-- Tab 1: 老人档案管理 -->
        <el-tab-pane label="老人档案" name="elders">
          <div class="toolbar-split">
            <el-form :inline="true" :model="elders.filters" class="toolbar-filters" @submit.prevent="elders.search">
              <el-form-item label="老人姓名">
                <el-input
                  v-model="elders.filters.name"
                  placeholder="输入姓名，回车查询"
                  clearable
                  @clear="elders.search"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="Search" native-type="submit">查询</el-button>
                <el-button v-if="elders.filters.name" icon="Refresh" @click="elders.reset">重置</el-button>
              </el-form-item>
            </el-form>
            <div class="toolbar-actions">
              <el-button type="primary" icon="Plus" @click="openElderModal(null)">录入老人档案</el-button>
            </div>
          </div>

          <DataTable
            :rows="elders.rows.value"
            :loading="elders.loading.value"
            :error="elders.error.value"
            :total="elders.total.value"
            :page-num="elders.pageNum.value"
            :page-size="elders.pageSize.value"
            empty-text="还没有符合条件的老人档案"
            error-text="老人档案暂时未能加载"
            @update:page-num="elders.setPage"
            @update:page-size="elders.setPageSize"
            @retry="elders.load"
          >
            <el-table-column prop="name" label="姓名" min-width="120">
              <template #default="{ row }">
                <span class="cell-title">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="gender" label="性别" width="80">
              <template #default="{ row }">
                {{ row.gender === 'FEMALE' ? '女' : '男' }}
              </template>
            </el-table-column>
            <el-table-column prop="age" label="年龄" width="80" />
            <el-table-column label="联系电话" width="170">
              <template #default="{ row }"><MaskedText :value="row.phone" /></template>
            </el-table-column>
            <el-table-column label="身份证号" width="190">
              <template #default="{ row }">
                <span class="id-card">{{ row.idCardMasked || '未登记' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="community" label="所属社区" min-width="140" class-name="hide-sm" />
            <el-table-column
              prop="address"
              label="居住详细地址"
              min-width="180"
              show-overflow-tooltip
              class-name="hide-sm"
            />
            <el-table-column label="紧急联系人" width="210">
              <template #default="{ row }">
                {{ row.emergencyContact || '—' }}
                <MaskedText v-if="row.emergencyPhone" :value="row.emergencyPhone" />
              </template>
            </el-table-column>
            <el-table-column
              prop="healthNote"
              label="健康及照护需求"
              min-width="160"
              show-overflow-tooltip
              class-name="hide-sm"
            />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="openElderModal(row)">编辑</el-button>
              </template>
            </el-table-column>
          </DataTable>
        </el-tab-pane>

        <!-- Tab 2: 家属代约绑定审核 -->
        <el-tab-pane label="家属代约审核" name="bindings">
          <div class="toolbar-split">
            <div class="toolbar-filters">
              <el-select
                v-model="bindings.filters.status"
                placeholder="全部审核状态"
                clearable
                style="width: 180px"
                @change="bindings.search"
              >
                <el-option v-for="option in bindingStatusOptions" :key="option.value" v-bind="option" />
              </el-select>
            </div>
          </div>
          <DataTable
            :rows="bindings.rows.value"
            :loading="bindings.loading.value"
            :error="bindings.error.value"
            :total="bindings.total.value"
            :page-num="bindings.pageNum.value"
            :page-size="bindings.pageSize.value"
            empty-text="暂无家属代约申请"
            error-text="家属申请暂时未能加载"
            @update:page-num="bindings.setPage"
            @update:page-size="bindings.setPageSize"
            @retry="bindings.load"
          >
            <el-table-column label="老人姓名" width="140">
              <template #default="{ row }">
                <span class="cell-title">{{ row.elder ? row.elder.name : '老人' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="申请家属" width="220">
              <template #default="{ row }">
                {{ row.familyUser ? row.familyUser.realName || row.familyUser.username : '' }}
                <MaskedText v-if="row.familyUser?.phone" :value="row.familyUser.phone" />
              </template>
            </el-table-column>
            <el-table-column prop="relation" label="亲属关系" width="120">
              <template #default="{ row }">
                <el-tag type="info">{{ row.relation }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="proofMaterials" label="关系说明 / 证明" min-width="180" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="申请时间" width="170" />
            <el-table-column prop="status" label="审核状态" width="120">
              <template #default="{ row }">
                <StatusTag kind="binding" :value="row.status" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button
                    type="primary"
                    link
                    :disabled="bindingAction.pendingKey.value !== null"
                    @click="auditBinding(row.id, true)"
                  >
                    通过
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    :disabled="bindingAction.pendingKey.value !== null"
                    @click="auditBinding(row.id, false)"
                  >
                    驳回
                  </el-button>
                </template>
                <template v-else-if="row.status === 'APPROVED'">
                  <el-button
                    type="danger"
                    link
                    :loading="bindingAction.pendingKey.value === row.id"
                    @click="revokeBinding(row.id)"
                  >
                    撤销授权
                  </el-button>
                </template>
              </template>
            </el-table-column>
          </DataTable>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 老人档案编辑弹窗 -->
    <el-dialog
      v-model="elderModalVisible"
      :title="currentElder.id ? '编辑老人档案' : '录入老人档案'"
      width="600px"
      @closed="elderFormRef?.clearValidate()"
    >
      <el-form ref="elderFormRef" :model="currentElder" :rules="elderRules" label-width="100px">
        <el-form-item label="老人姓名" prop="name">
          <el-input v-model="currentElder.name" placeholder="真实姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="currentElder.gender">
            <el-radio value="MALE">男</el-radio>
            <el-radio value="FEMALE">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="currentElder.age" :min="0" :max="130" />
        </el-form-item>
        <el-form-item label="居住地址" prop="address">
          <el-input v-model="currentElder.address" placeholder="详细居住地址" maxlength="255" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="currentElder.phone" placeholder="本人手机号或座机" />
        </el-form-item>
        <el-form-item label="紧急联系人" prop="emergencyContact">
          <el-input v-model="currentElder.emergencyContact" placeholder="姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系人电话" prop="emergencyPhone">
          <el-input v-model="currentElder.emergencyPhone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="健康需求说明" prop="healthNote">
          <el-input
            v-model="currentElder.healthNote"
            type="textarea"
            maxlength="2000"
            show-word-limit
            placeholder="日常注意事项及需求"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="elderModalVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveElder.pending.value" @click="saveElder.run">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { COMMUNITY_NAME } from '../../utils/app-config.js'
import PageHeader from '../../components/PageHeader.vue'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import MaskedText from '../../components/MaskedText.vue'
import { computed, ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { statusOptions } from '../../utils/presentation.js'
import { usePagedQuery } from '../../composables/usePagedQuery.js'
import { useRowSubmit, useSubmit } from '../../composables/useSubmit.js'
import { confirmDanger, promptReason } from '../../utils/confirm.js'
import { maxLength, phoneRule, required } from '../../utils/validators.js'
import { pickElderPayload } from './elder-payload.js'

const route = useRoute()
const router = useRouter()
const bindingStatusOptions = statusOptions('binding')

// 页签与 ?tab= 同步（RT4）
const activeTab = computed(() => (route.query.tab === 'bindings' ? 'bindings' : 'elders'))
function switchTab(tab) {
  const query = { ...route.query }
  if (tab === 'bindings') query.tab = 'bindings'
  else delete query.tab
  router.replace({ query })
}

const elders = usePagedQuery({
  fetcher: (params, { signal }) => request.get('/elders/page', { params, signal }),
  defaults: { name: '' }
})
const bindings = usePagedQuery({
  fetcher: (params, { signal }) => request.get('/family-bindings/page', { params, signal }),
  defaults: { status: '' },
  prefix: 'b_'
})

const elderModalVisible = ref(false)
const elderFormRef = ref(null)
const emptyElder = () => ({
  id: null,
  name: '',
  gender: 'MALE',
  age: 80,
  phone: '',
  address: COMMUNITY_NAME,
  community: COMMUNITY_NAME,
  emergencyContact: '',
  emergencyPhone: '',
  healthNote: ''
})
const currentElder = reactive(emptyElder())
const elderRules = {
  name: [required('请填写老人姓名'), maxLength(64, '姓名')],
  address: [required('请填写居住地址'), maxLength(255, '地址')],
  phone: [phoneRule('联系电话', { optional: true })],
  emergencyContact: [required('请填写紧急联系人'), maxLength(64, '紧急联系人')],
  emergencyPhone: [phoneRule('联系人电话')],
  healthNote: [maxLength(2000, '健康需求说明')]
}

function openElderModal(row) {
  Object.assign(currentElder, emptyElder(), row ? pickElderPayload(row) : {})
  elderModalVisible.value = true
}

const saveElder = useSubmit(async () => {
  const valid = await elderFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    // 只提交表单字段（白名单），不回传身份证号等未编辑字段
    await request.post('/elders/save', pickElderPayload(currentElder))
    ElMessage.success('老人档案保存成功')
    elderModalVisible.value = false
    elders.load()
  } catch {}
})

const bindingAction = useRowSubmit(async (bindingId, action) => {
  try {
    await action(bindingId)
    bindings.load()
  } catch {}
})

async function auditBinding(bindingId, approve) {
  const actionText = approve ? '通过' : '驳回'
  // 理由留空必填，避免预填文案被直接提交进审计记录（I3）
  const reason = await promptReason(`请输入${actionText}审核备注说明`, '审核家属代约申请', {
    confirmText: `确认${actionText}`,
    danger: !approve,
    placeholder: approve ? '例如：已核实亲属关系' : '例如：缺少户口本或关系证明'
  })
  if (reason === null) return
  bindingAction.run(bindingId, async id => {
    await request.post('/family-bindings/audit', { bindingId: id, approve, reason })
    ElMessage.success(`已${actionText}`)
  })
}

async function revokeBinding(bindingId) {
  const ok = await confirmDanger('撤销后家属将不能再为这位老人预约服务。', '撤销家属授权', '确认撤销')
  if (!ok) return
  bindingAction.run(bindingId, async id => {
    await request.post(`/family-bindings/${id}/revoke`)
    ElMessage.success('已撤销')
  })
}
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.id-card {
  font-variant-numeric: tabular-nums;
  color: var(--muted);
}
</style>
