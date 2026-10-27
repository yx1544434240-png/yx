<template>
  <div class="orders-container">
    <PageHeader
      eyebrow="服务订单"
      title="每一次服务，都有迹可循"
      description="查看预约进度、协调人员安排，跟进服务完成情况。"
    />
    <el-card shadow="never" class="main-card">
      <div class="toolbar-split">
        <el-form :inline="true" :model="list.filters" class="toolbar-filters" @submit.prevent="list.search">
          <el-form-item label="订单编号">
            <el-input
              v-model="list.filters.orderNo"
              placeholder="输入订单号，回车查询"
              clearable
              @clear="list.search"
            />
          </el-form-item>
          <el-form-item label="订单状态">
            <el-select
              v-model="list.filters.status"
              placeholder="全部状态"
              clearable
              style="width: 160px"
              @change="list.search"
            >
              <el-option v-for="option in orderStatusOptions" :key="option.value" v-bind="option" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" native-type="submit">查询</el-button>
            <el-button v-if="list.filters.orderNo || list.filters.status" icon="Refresh" @click="list.reset">
              重置
            </el-button>
          </el-form-item>
        </el-form>
        <div class="toolbar-actions">
          <el-button icon="Refresh" :loading="list.loading.value" @click="list.load">刷新</el-button>
        </div>
      </div>

      <DataTable
        :rows="list.rows.value"
        :loading="list.loading.value"
        :error="list.error.value"
        :total="list.total.value"
        :page-num="list.pageNum.value"
        :page-size="list.pageSize.value"
        empty-text="没有符合条件的订单"
        error-text="订单暂时未能加载"
        @update:page-num="list.setPage"
        @update:page-size="list.setPageSize"
        @retry="list.load"
      >
        <el-table-column prop="order.serviceNameSnapshot" label="服务项目 / 订单编号" min-width="215">
          <template #default="{ row }">
            <div class="cell-title">{{ row.order.serviceNameSnapshot }}</div>
            <div class="cell-note">{{ row.order.orderNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="elder.name" label="服务老人" min-width="120">
          <template #default="{ row }">
            <span>{{ row.elder ? row.elder.name : '老人' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="order.plannedStartAt" label="预约时间" min-width="170">
          <template #default="{ row }">
            <span :title="row.order.plannedStartAt">{{ shortDate(row.order.plannedStartAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="order.status" label="状态" width="120">
          <template #default="{ row }">
            <StatusTag kind="order" :value="row.order.status" />
          </template>
        </el-table-column>
        <el-table-column label="服务人员" min-width="120">
          <template #default="{ row }">
            <span v-if="row.currentStaff">{{ row.currentStaff.user.realName }}</span>
            <span v-else class="text-muted">尚未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="145" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.order.id)">查看</el-button>
            <el-dropdown
              v-if="canReassign(row.order.status) || canCancel(row.order.status) || row.order.status === 'WAIT_CONFIRM'"
              trigger="click"
              class="row-more"
            >
              <el-button link aria-label="更多订单操作" :loading="rowAction.pendingKey.value === row.order.id">
                更多
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canReassign(row.order.status)" @click="openReassign(row)">
                    安排改派
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.order.status === 'WAIT_CONFIRM'" @click="adminConfirm(row.order.id)">
                    代确认完成
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="canCancel(row.order.status)"
                    divided
                    class="is-danger"
                    @click="handleCancel(row.order.id)"
                  >
                    取消订单
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </DataTable>
    </el-card>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="订单全流程追踪与详情" size="600px" @closed="closeDetail">
      <div v-if="detailLoading && !detailData" class="drawer-body">
        <el-skeleton :rows="8" animated />
      </div>
      <div v-else-if="detailData" v-loading="detailLoading" class="drawer-body">
        <section class="drawer-hero">
          <div class="drawer-hero-top">
            <div>
              <span class="drawer-order-no">{{ detailData.order.orderNo }}</span>
              <h3 class="drawer-service-title">{{ detailData.order.serviceNameSnapshot }}</h3>
            </div>
            <StatusTag kind="order" :value="detailData.order.status" />
          </div>
          <div class="drawer-hero-meta">
            <div class="drawer-meta-item">
              <span class="drawer-meta-label">预计费用</span>
              <strong class="text-warm">¥{{ detailData.order.priceSnapshot }}</strong>
            </div>
            <div class="drawer-meta-item">
              <span class="drawer-meta-label">预约上门</span>
              <strong>{{ shortDate(detailData.order.plannedStartAt) }}</strong>
            </div>
            <div class="drawer-meta-item">
              <span class="drawer-meta-label">服务对象</span>
              <strong>{{ detailData.elder ? detailData.elder.name : '—' }}</strong>
            </div>
          </div>
        </section>

        <section class="drawer-section">
          <div class="drawer-section-title">预约与联系信息</div>
          <dl class="drawer-grid">
            <div class="drawer-field">
              <dt>费用协调人</dt>
              <dd>{{ detailData.payerName || '下单人' }}</dd>
            </div>
            <div class="drawer-field">
              <dt>调度轮次</dt>
              <dd>第 {{ detailData.order.dispatchRound }} 轮</dd>
            </div>
            <div class="drawer-field is-full">
              <dt>联系人与电话</dt>
              <dd>
                {{ detailData.order.contactNameSnapshot }}
                <MaskedText :value="detailData.order.contactPhoneSnapshot" />
              </dd>
            </div>
            <div class="drawer-field is-full">
              <dt>上门地址</dt>
              <dd>{{ detailData.order.addressSnapshot }}</dd>
            </div>
            <div v-if="detailData.settlementNote" class="drawer-field is-full">
              <dt>结算说明</dt>
              <dd class="text-muted">{{ detailData.settlementNote }}</dd>
            </div>
          </dl>
        </section>

        <!-- 履约记录 -->
        <section v-if="detailData.serviceRecord" class="drawer-section">
          <div class="drawer-section-title">现场履约记录</div>
          <dl class="drawer-grid">
            <div class="drawer-field is-full">
              <dt>实际起止时间</dt>
              <dd>{{ detailData.serviceRecord.actualStartAt }} ~ {{ detailData.serviceRecord.actualEndAt }}</dd>
            </div>
            <div class="drawer-field is-full">
              <dt>现场工作总结</dt>
              <dd class="drawer-summary-box">{{ detailData.serviceRecord.serviceSummary }}</dd>
            </div>
            <div v-if="photoPaths.length" class="drawer-field is-full">
              <dt>现场照片凭证</dt>
              <dd class="drawer-photos">
                <PrivatePhoto
                  v-for="source in photoPaths"
                  :key="source"
                  :source="source"
                  :preview-list="loadedPhotoUrls"
                  @loaded="onPhotoLoaded"
                />
              </dd>
            </div>
          </dl>
        </section>

        <!-- 评价反馈 -->
        <section v-if="detailData.serviceReview" class="drawer-section">
          <div class="drawer-section-title">客户评价</div>
          <dl class="drawer-grid">
            <div class="drawer-field is-full">
              <dt>满意度星级</dt>
              <dd>
                <el-rate :model-value="detailData.serviceReview.rating" disabled show-score text-color="var(--warm)" />
              </dd>
            </div>
            <div class="drawer-field is-full">
              <dt>评价内容</dt>
              <dd>
                {{ detailData.serviceReview.content || '无文字评价' }}
                <div v-if="detailData.serviceReview.tags" class="review-tags">
                  <el-tag v-for="tag in detailData.serviceReview.tags.split(',')" :key="tag" size="small">
                    {{ tag }}
                  </el-tag>
                </div>
              </dd>
            </div>
          </dl>
        </section>

        <!-- 状态流转日志时间线 -->
        <section class="drawer-section">
          <div class="drawer-section-title">状态流转轨迹与日志</div>
          <el-timeline class="drawer-timeline">
            <el-timeline-item v-for="log in detailData.logs" :key="log.id" :timestamp="log.createdAt" type="primary">
              <strong class="cell-title">{{ statusText('order', log.toStatus) }}</strong>
              <div class="text-muted text-sm timeline-reason">{{ log.reason }}</div>
            </el-timeline-item>
          </el-timeline>
        </section>
      </div>
    </el-drawer>

    <!-- 改派对话框 -->
    <el-dialog v-model="reassignVisible" title="人工调度改派" width="500px" @closed="reassignFormRef?.clearValidate()">
      <el-form ref="reassignFormRef" :model="reassignForm" :rules="reassignRules" label-width="100px">
        <el-form-item label="改派给人员" prop="newStaffId">
          <StaffSelect v-model="reassignForm.newStaffId" />
        </el-form-item>
        <el-form-item label="改派原因" prop="reason">
          <el-input
            v-model="reassignForm.reason"
            type="textarea"
            maxlength="200"
            show-word-limit
            placeholder="填写人工改派原因留档（至少 4 个字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reassignVisible = false">取消</el-button>
        <el-button type="primary" :loading="reassign.pending.value" @click="reassign.run">确认改派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import PageHeader from '../../components/PageHeader.vue'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import MaskedText from '../../components/MaskedText.vue'
import StaffSelect from '../../components/StaffSelect.vue'
import PrivatePhoto from '../../components/PrivatePhoto.vue'
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { shortDate, statusOptions, statusText } from '../../utils/presentation.js'
import { usePagedQuery } from '../../composables/usePagedQuery.js'
import { useRowSubmit, useSubmit } from '../../composables/useSubmit.js'
import { promptReason } from '../../utils/confirm.js'
import { required } from '../../utils/validators.js'

const route = useRoute()
const router = useRouter()
const orderStatusOptions = statusOptions('order')
const list = usePagedQuery({
  fetcher: (params, { signal }) => request.get('/orders/page', { params, signal }),
  defaults: { orderNo: '', status: '' },
  pageSize: 10
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)
const photoBlobMap = reactive({})
const photoPaths = computed(() => {
  try {
    const values = JSON.parse(detailData.value?.serviceRecord?.attachmentsJson || '[]')
    return Array.isArray(values) ? values.filter(value => typeof value === 'string').slice(0, 4) : []
  } catch {
    return []
  }
})
const loadedPhotoUrls = computed(() => photoPaths.value.map(src => photoBlobMap[src]).filter(Boolean))
function onPhotoLoaded(source, blobUrl) {
  if (blobUrl) photoBlobMap[source] = blobUrl
  else delete photoBlobMap[source]
}

const reassignVisible = ref(false)
const reassignFormRef = ref(null)
const reassignForm = reactive({ orderId: null, newStaffId: null, reason: '' })
const reassignRules = {
  newStaffId: [required('请选择改派的服务人员', 'change')],
  reason: [required('请填写改派原因'), { min: 4, max: 200, message: '改派原因为 4 至 200 字', trigger: 'blur' }]
}

const ACTIVE = ['WAIT_DISPATCH', 'WAIT_ACCEPT', 'WAIT_VISIT', 'WAIT_MANUAL']
const canCancel = status => ACTIVE.includes(status)
const canReassign = status => ACTIVE.includes(status)

// 详情抽屉与 ?orderId= 同步：可分享、可后退关闭，同页切换订单也会更新（RT4）
let detailSeq = 0
let detailController = null
async function loadDetail(id) {
  const seq = ++detailSeq
  detailController?.abort()
  detailController = typeof AbortController === 'undefined' ? null : new AbortController()
  if (detailData.value?.order?.id && String(detailData.value.order.id) !== String(id)) {
    detailData.value = null
  }
  for (const key of Object.keys(photoBlobMap)) delete photoBlobMap[key]
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const data = await request.get(`/orders/${id}`, { signal: detailController?.signal })
    if (seq !== detailSeq) return
    detailData.value = data
  } catch (err) {
    if (seq === detailSeq && err?.kind !== 'canceled') closeDetail()
  } finally {
    if (seq === detailSeq) detailLoading.value = false
  }
}
function openDetail(id) {
  router.push({ query: { ...route.query, orderId: String(id) } })
}
function closeDetail() {
  detailSeq++
  detailController?.abort()
  detailLoading.value = false
  drawerVisible.value = false
  if (route.query.orderId) {
    const query = { ...route.query }
    delete query.orderId
    router.replace({ query })
  }
}
watch(
  () => route.query.orderId,
  id => {
    if (id) loadDetail(String(id))
    else drawerVisible.value = false
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  detailSeq++
  detailController?.abort()
})

function openReassign(row) {
  Object.assign(reassignForm, { orderId: row.order.id, newStaffId: null, reason: '' })
  reassignVisible.value = true
}

const reassign = useSubmit(async () => {
  const valid = await reassignFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await request.post('/dispatch/reassign', { ...reassignForm, reason: reassignForm.reason.trim() })
    ElMessage.success('人工改派成功')
    reassignVisible.value = false
    list.load()
  } catch {}
})

const rowAction = useRowSubmit(async (orderId, action) => {
  try {
    await action(orderId)
    list.load()
  } catch {}
})

async function adminConfirm(orderId) {
  const note = await promptReason('请输入管理员代为确认的依据与情况说明', '管理员代确认服务完成', {
    confirmText: '确认完成',
    min: 10,
    max: 220,
    placeholder: '填写实际核验方式、结果及代确认原因（10至220字）'
  })
  if (note === null) return
  rowAction.run(orderId, async id => {
    await request.post('/fulfillment/confirm', { orderId: id, note })
    ElMessage.success('代确认完成成功')
  })
}

async function handleCancel(orderId) {
  const reason = await promptReason(
    '取消后服务人员的排班预留会立即释放，居民会收到取消通知。请填写取消原因：',
    '取消订单',
    { confirmText: '确认取消订单', danger: true, max: 100, placeholder: '例如：居民来电要求取消' }
  )
  if (reason === null) return
  rowAction.run(orderId, async id => {
    await request.post(`/orders/${id}/cancel`, null, { params: { reason } })
    ElMessage.success('订单已取消')
  })
}
</script>

<style scoped>
.drawer-body {
  padding: 4px 6px 18px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.drawer-hero {
  background: var(--brand-soft);
  border: 1px solid #d5e6dc;
  border-radius: 12px;
  padding: 18px 20px;
}

.drawer-hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.drawer-order-no {
  display: block;
  font-size: var(--fs-xs);
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.drawer-service-title {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 600;
  color: var(--ink);
}

.drawer-hero-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #d8e7de;
}

.drawer-meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-meta-label {
  font-size: var(--fs-xs);
  color: var(--muted);
}

.drawer-meta-item strong {
  font-size: var(--fs-base);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.drawer-section {
  background: var(--surface-muted);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
}

.drawer-section-title {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 14px;
}

.drawer-grid {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.drawer-field {
  margin: 0;
}

.drawer-field.is-full {
  grid-column: 1 / -1;
}

.drawer-field dt {
  font-size: var(--fs-xs);
  color: var(--muted);
  margin-bottom: 4px;
}

.drawer-field dd {
  margin: 0;
  font-size: var(--fs-base);
  color: var(--ink);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.drawer-summary-box {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
}

.drawer-photos {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.review-tags {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drawer-timeline {
  padding-left: 4px;
  margin-top: 4px;
}

.timeline-reason {
  margin-top: 4px;
}
</style>
