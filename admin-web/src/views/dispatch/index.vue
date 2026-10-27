<template>
  <section class="dispatch-page">
    <PageHeader
      eyebrow="服务与人员，有序连接"
      title="智能派单"
      description="查看待安排的预约，为每一项服务找到合适的人。"
    >
      <template #actions>
        <el-button :loading="loading" @click="fetchData">
          <el-icon><Refresh /></el-icon>
          <span>刷新队列</span>
        </el-button>
      </template>
    </PageHeader>
    <div class="dispatch-layout">
      <section class="panel queue-panel">
        <div class="panel-heading">
          <h2>
            待安排服务
            <span class="queue-total">{{ queue.total.value }}</span>
          </h2>
          <span class="queue-hint">选择预约查看推荐</span>
        </div>
        <div class="queue-filters" role="group" aria-label="订单状态">
          <button
            v-for="filter in filters"
            :key="filter.value"
            :class="{ active: queue.filters.queue === filter.value }"
            :aria-pressed="queue.filters.queue === filter.value"
            @click="changeFilter(filter.value)"
          >
            {{ filter.label }}
          </button>
        </div>
        <div v-loading="loading && tableData.length > 0" class="queue-body">
          <el-skeleton v-if="loading && !tableData.length" animated :rows="5" style="padding: 16px" />
          <button
            v-for="row in tableData"
            :key="row.order.id"
            class="queue-item"
            :class="{ selected: selectedOrder?.order.id === row.order.id }"
            @click="selectOrder(row)"
          >
            <span class="queue-item-top">
              <strong>{{ row.order.serviceNameSnapshot }}</strong>
              <span class="status-label" :class="row.order.status === 'WAIT_MANUAL' ? 'is-amber' : ''">
                {{ orderStatusText[row.order.status] }}
              </span>
            </span>
            <span class="queue-person">
              {{ row.elder?.name || '服务对象' }}
              <span>第 {{ row.order.dispatchRound || 1 }} 轮安排</span>
            </span>
            <span class="queue-meta">
              <el-icon><Calendar /></el-icon>
              {{ shortDate(row.order.plannedStartAt) }}
            </span>
            <span class="queue-meta queue-address">
              <el-icon><Location /></el-icon>
              {{ row.order.addressSnapshot }}
            </span>
          </button>
          <div v-if="!tableData.length && !loading" class="empty-state">
            <el-icon><CircleCheck /></el-icon>
            <strong>{{ queueError ? '暂时未能加载预约' : '暂无需要安排的服务' }}</strong>
            <p>{{ queueError ? '点击上方刷新重新加载。' : '新的待安排预约会在这里出现。' }}</p>
          </div>
        </div>
        <div v-if="queue.total.value" class="queue-pagination">
          <el-pagination
            :current-page="queue.pageNum.value"
            :page-size="queue.pageSize.value"
            :total="queue.total.value"
            layout="prev, pager, next"
            :pager-count="5"
            small
            @current-change="queue.setPage"
          />
        </div>
      </section>
      <div class="decision-column">
        <template v-if="selectedOrder">
          <section class="panel selected-service">
            <div class="selected-service-head">
              <span class="eyebrow">当前服务预约</span>
              <span class="order-reference">{{ selectedOrder.order.orderNo }}</span>
            </div>
            <div class="selected-service-main">
              <div>
                <h2>{{ selectedOrder.order.serviceNameSnapshot }}</h2>
                <p>
                  {{ selectedOrder.elder?.name || '服务对象' }}
                  <span class="detail-separator">/</span>
                  {{ selectedOrder.order.durationMinutesSnapshot }} 分钟
                  <span class="detail-separator">/</span>
                  ¥{{ selectedOrder.order.priceSnapshot }}
                </p>
              </div>
              <span class="status-label" :class="selectedOrder.order.status === 'WAIT_MANUAL' ? 'is-amber' : ''">
                <span class="status-dot"></span>
                {{ orderStatusText[selectedOrder.order.status] }}
              </span>
            </div>
            <div class="appointment-facts">
              <span>
                <el-icon><Calendar /></el-icon>
                {{ shortDate(selectedOrder.order.plannedStartAt) }}
              </span>
              <span>
                <el-icon><Location /></el-icon>
                {{ selectedOrder.order.addressSnapshot }}
              </span>
            </div>
            <div class="selection-actions">
              <el-button type="primary" :loading="actionLoading" @click="autoDispatch(selectedOrder.order.id)">
                <el-icon><Connection /></el-icon>
                <span>重新自动匹配</span>
              </el-button>
              <el-button @click="openAssign(selectedOrder)">手动选择人员</el-button>
            </div>
          </section>
          <section v-loading="scoresLoading" class="panel candidates-panel">
            <div class="panel-heading">
              <div>
                <h2>人员匹配建议</h2>
                <p>综合考虑服务能力、路程、工作安排与熟悉程度</p>
              </div>
              <span class="qualified-count">{{ qualifiedCandidates.length }} 位可安排</span>
            </div>
            <div v-if="qualifiedCandidates.length" class="candidate-list">
              <article
                v-for="(candidate, index) in qualifiedCandidates"
                :key="candidate.staffId"
                class="candidate-card"
                :class="{ recommended: index === 0 }"
              >
                <div class="candidate-top">
                  <div class="candidate-person">
                    <span class="person-avatar">{{ staffName(candidate).slice(-2) }}</span>
                    <div>
                      <div class="candidate-name">
                        {{ staffName(candidate) }}
                        <span v-if="index === 0" class="recommended-label">最优匹配</span>
                      </div>
                      <p>{{ staffArea(candidate) }}</p>
                    </div>
                  </div>
                  <div class="match-score">
                    <strong>{{ scorePercent(candidate.totalScore) }}</strong>
                    <span>综合匹配分</span>
                  </div>
                </div>
                <div class="candidate-facts">
                  <span>
                    <el-icon><CircleCheck /></el-icon>
                    技能符合要求
                  </span>
                  <span>
                    <el-icon><Location /></el-icon>
                    预计路程 {{ candidate.travelMinutes }} 分钟
                  </span>
                  <span>
                    <el-icon><Calendar /></el-icon>
                    预约时段可安排
                  </span>
                  <span>
                    <el-icon><User /></el-icon>
                    {{ Number(candidate.metricF) > 0 ? '曾为该老人提供服务' : '首次为该老人服务' }}
                  </span>
                </div>
                <div class="score-bars" aria-label="多维匹配指标">
                  <div class="score-bar-item">
                    <div class="score-bar-head">
                      <span>路程就近</span>
                      <span class="stat-number">{{ scorePercent(candidate.metricT) }}%</span>
                    </div>
                    <div class="score-bar-track">
                      <span class="score-bar-fill" :style="{ width: `${scorePercent(candidate.metricT)}%` }"></span>
                    </div>
                  </div>
                  <div class="score-bar-item">
                    <div class="score-bar-head">
                      <span>质量评分</span>
                      <span class="stat-number">{{ scorePercent(candidate.metricQ) }}%</span>
                    </div>
                    <div class="score-bar-track">
                      <span class="score-bar-fill" :style="{ width: `${scorePercent(candidate.metricQ)}%` }"></span>
                    </div>
                  </div>
                  <div class="score-bar-item">
                    <div class="score-bar-head">
                      <span>工作余量</span>
                      <span class="stat-number">{{ scorePercent(candidate.metricL) }}%</span>
                    </div>
                    <div class="score-bar-track">
                      <span class="score-bar-fill" :style="{ width: `${scorePercent(candidate.metricL)}%` }"></span>
                    </div>
                  </div>
                  <div class="score-bar-item">
                    <div class="score-bar-head">
                      <span>长辈熟悉度</span>
                      <span class="stat-number">{{ scorePercent(candidate.metricF) }}%</span>
                    </div>
                    <div class="score-bar-track">
                      <span class="score-bar-fill" :style="{ width: `${scorePercent(candidate.metricF)}%` }"></span>
                    </div>
                  </div>
                </div>
                <div class="candidate-bottom">
                  <span>
                    服务质量匹配 {{ scorePercent(candidate.metricQ) }}%
                    <span class="small-divider">·</span>
                    工作余量 {{ scorePercent(candidate.metricL) }}%
                  </span>
                  <el-button
                    :type="index === 0 ? 'primary' : 'default'"
                    @click="openAssign(selectedOrder, candidate.staffId)"
                  >
                    选择此人
                    <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
              </article>
            </div>
            <div v-else-if="!scoresLoading" class="empty-state">
              <el-icon><Search /></el-icon>
              <strong>{{ scoreError ? '匹配信息暂时无法加载' : '暂未找到合适的服务人员' }}</strong>
              <p>{{ scoreError ? '请重新选择预约，或刷新后再试。' : '可查看人员排除原因，或更新安排后重新匹配。' }}</p>
            </div>
            <el-collapse v-if="excludedCandidates.length" class="excluded-list">
              <el-collapse-item :title="`查看 ${excludedCandidates.length} 位暂不可安排人员的原因`" name="excluded">
                <div v-for="candidate in excludedCandidates" :key="candidate.staffId" class="excluded-person">
                  <span>{{ staffName(candidate) }}</span>
                  <p>{{ candidate.excludeReason || '暂不满足本次服务要求' }}</p>
                </div>
              </el-collapse-item>
            </el-collapse>
          </section>
          <div class="dispatch-footnote">
            <el-icon><CircleCheck /></el-icon>
            <span>发出派单后，服务人员需确认接单；未接单的服务将按规则继续安排。</span>
          </div>
        </template>
        <section v-else class="panel select-placeholder empty-state">
          <el-icon><Connection /></el-icon>
          <strong>选择一项待安排的服务</strong>
          <p>这里将展示预约详情与合适的服务人员。</p>
        </section>
      </div>
    </div>
    <el-dialog v-model="assignVisible" title="确认安排人员" width="500px">
      <p class="assign-context">
        {{ selectedOrder?.order.serviceNameSnapshot }} · {{ selectedOrder?.elder?.name || '服务对象' }}
      </p>
      <el-form :model="assignForm" label-position="top">
        <el-form-item label="服务人员">
          <StaffSelect v-model="assignForm.staffId" :seed="staffList" />
        </el-form-item>
      </el-form>
      <p class="assign-note">确认后将向该人员发送派单，系统会再次检查技能与时间安排。</p>
      <template #footer>
        <el-button @click="assignVisible = false">暂不安排</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitAssign">确认派单</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import PageHeader from '../../components/PageHeader.vue'
import StaffSelect from '../../components/StaffSelect.vue'
import { computed, ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { latestCandidateScores, orderStatusText, shortDate } from '../../utils/presentation'
import { usePagedQuery } from '../../composables/usePagedQuery.js'

const filters = [
  { label: '全部待办', value: 'WAIT_MANUAL,WAIT_DISPATCH' },
  { label: '需要协调', value: 'WAIT_MANUAL' },
  { label: '等待安排', value: 'WAIT_DISPATCH' }
]
const validQueues = new Set(filters.map(f => f.value))

// 复用通用分页状态机（RT4/C1）：支持竞态取消、最后一页自动回退与 URL 同步
const queue = usePagedQuery({
  fetcher: ({ pageNum, pageSize, queue: queueVal }, { signal }) => {
    const status = validQueues.has(queueVal) ? queueVal : filters[0].value
    return request.get('/orders/page', {
      params: { pageNum, pageSize, status },
      signal
    })
  },
  defaults: { queue: filters[0].value },
  pageSize: 6
})

const loading = queue.loading
const queueError = queue.error
const tableData = queue.rows

const scoresLoading = ref(false)
const scoreError = ref(false)
const actionLoading = ref(false)
const scoresList = ref([])
const selectedOrder = ref(null)
const assignVisible = ref(false)
const staffList = ref([])

let scoreController = null
let staffController = null
let pollTimer = null

const assignForm = reactive({ orderId: null, staffId: null })
const currentScores = computed(() => latestCandidateScores(scoresList.value))
const qualifiedCandidates = computed(() => currentScores.value.filter(item => Number(item.isQualified) === 1))
const excludedCandidates = computed(() => currentScores.value.filter(item => Number(item.isQualified) !== 1))
const staffById = computed(() => new Map(staffList.value.map(staff => [String(staff.profile.id), staff])))

function resolveCandidateId(candidateOrId) {
  return typeof candidateOrId === 'object' && candidateOrId !== null ? candidateOrId.staffId : candidateOrId
}
function staffName(candidateOrId) {
  if (typeof candidateOrId === 'object' && candidateOrId !== null) {
    if (candidateOrId.staffName) return candidateOrId.staffName
    if (candidateOrId.realName) return candidateOrId.realName
  }
  const id = resolveCandidateId(candidateOrId)
  return staffById.value.get(String(id))?.user?.realName || `服务人员 ${String(id).slice(-4)}`
}
function staffArea(candidateOrId) {
  if (typeof candidateOrId === 'object' && candidateOrId?.serviceArea) {
    return candidateOrId.serviceArea
  }
  const id = resolveCandidateId(candidateOrId)
  return staffById.value.get(String(id))?.profile?.serviceArea || '社区服务人员'
}
function scorePercent(value) {
  return Math.round(Math.min(1, Math.max(0, Number(value) || 0)) * 100)
}

watch(tableData, rows => {
  const current = rows.find(row => row.order.id === selectedOrder.value?.order.id) || rows[0]
  if (current) selectOrder(current)
  else {
    scoreController?.abort()
    selectedOrder.value = null
    scoresList.value = []
  }
})

function fetchData() {
  return queue.load()
}
function changeFilter(status) {
  queue.filters.queue = status
  return queue.search()
}
async function selectOrder(row) {
  selectedOrder.value = row
  scoresList.value = []
  scoresLoading.value = true
  scoreError.value = false
  scoreController?.abort()
  const controller = (scoreController = typeof AbortController === 'undefined' ? null : new AbortController())
  const id = row.order.id
  try {
    const scores = await request.get(`/dispatch/scores/${id}`, { signal: controller?.signal })
    if (selectedOrder.value?.order.id === id) scoresList.value = scores || []
  } catch (err) {
    if (selectedOrder.value?.order.id === id && err?.kind !== 'canceled') scoreError.value = true
  } finally {
    if (selectedOrder.value?.order.id === id && scoreController === controller) scoresLoading.value = false
  }
}
async function loadStaff() {
  staffController?.abort()
  const controller = (staffController = typeof AbortController === 'undefined' ? null : new AbortController())
  try {
    const result = await request.get('/staff/page', {
      params: { pageNum: 1, pageSize: 100, auditStatus: 'APPROVED' },
      signal: controller?.signal,
      silent: true
    })
    if (staffController === controller) staffList.value = result?.records || []
  } catch (err) {
    if (staffController === controller && err?.kind !== 'canceled') staffList.value = []
  }
}
function openAssign(row, staffId = null) {
  assignForm.orderId = row.order.id
  assignForm.staffId = staffId == null ? null : String(staffId)
  assignVisible.value = true
  if (!staffList.value.length) loadStaff()
}
async function submitAssign() {
  if (actionLoading.value) return
  if (!assignForm.staffId) {
    ElMessage.warning('请选择服务人员')
    return
  }
  actionLoading.value = true
  try {
    await request.post('/dispatch/assign', assignForm)
    ElMessage.success('派单已发送，等待人员确认')
    assignVisible.value = false
    await fetchData()
  } catch {
  } finally {
    actionLoading.value = false
  }
}
async function autoDispatch(orderId) {
  if (actionLoading.value) return
  actionLoading.value = true
  try {
    const attempt = await request.post('/dispatch/assign', { orderId, staffId: null })
    ElMessage[attempt ? 'success' : 'info'](attempt ? '派单已发送，等待人员确认' : '暂未匹配到合适人员，可查看排除原因')
    await fetchData()
  } catch {
  } finally {
    actionLoading.value = false
  }
}
onMounted(() => {
  if (!validQueues.has(queue.filters.queue)) queue.filters.queue = filters[0].value
  loadStaff()
  if (typeof document !== 'undefined') {
    pollTimer = setInterval(() => {
      if (document.visibilityState === 'visible' && !loading.value && !actionLoading.value && !assignVisible.value) {
        fetchData()
      }
    }, 30000)
  }
})
onBeforeUnmount(() => {
  clearInterval(pollTimer)
  scoreController?.abort()
  staffController?.abort()
})
</script>

<style scoped>
.dispatch-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.85fr) minmax(0, 1.4fr);
  gap: 24px;
  align-items: start;
}
.queue-panel {
  overflow: hidden;
}
.queue-panel .panel-heading {
  padding-bottom: 18px;
}
.queue-total {
  font-size: 12px;
  margin-left: 7px;
  font-weight: 500;
  color: var(--brand);
}
.queue-hint {
  font-size: 12px;
  color: var(--muted);
}
.queue-filters {
  margin: 0 20px 18px;
  display: flex;
  gap: 4px;
  border-radius: 7px;
  padding: 4px;
  background: #f2f5ee;
}
.queue-filters button {
  flex: 1;
  border: 0;
  padding: 8px 4px;
  border-radius: 5px;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.queue-filters button.active {
  background: #fff;
  color: var(--brand);
  box-shadow: 0 1px 4px #263d2710;
  font-weight: 550;
}
.queue-body {
  padding: 0 14px 7px;
  min-height: 220px;
}
.queue-item {
  position: relative;
  display: block;
  width: 100%;
  padding: 19px 16px;
  margin-bottom: 8px;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 10px;
  background: none;
  color: var(--ink);
  cursor: pointer;
  transition: background 0.15s;
}
.queue-item:hover {
  background: #f7f9f3;
}
.queue-item.selected {
  background: #edf4ed;
  border-color: #d9e5d6;
}
.queue-item.selected::before {
  content: '';
  position: absolute;
  height: 25px;
  width: 3px;
  background: var(--brand);
  left: -1px;
  top: 20px;
  border-radius: 0 3px 3px 0;
}
.queue-item-top,
.queue-person {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.queue-item-top strong {
  font-size: 14px;
  font-weight: 600;
}
.queue-item-top .status-label {
  font-size: 12px;
  padding: 3px 6px;
}
.queue-person {
  margin: 12px 0;
  font-size: 12px;
}
.queue-person > span {
  color: var(--muted);
  font-size: 12px;
}
.queue-meta {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 6px;
}
.queue-meta .el-icon {
  margin-top: 2px;
  font-size: 13px;
  flex-shrink: 0;
}
.queue-address {
  overflow-wrap: anywhere;
}
.queue-pagination {
  border-top: 1px solid var(--line);
  padding: 15px 12px;
  display: flex;
  justify-content: center;
}
.decision-column {
  min-width: 0;
}
.selected-service {
  padding: 25px 26px;
  margin-bottom: 21px;
}
.selected-service-head {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
}
.order-reference {
  color: #7a877b;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.selected-service-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 15px;
  margin-top: 7px;
}
.selected-service-main h2 {
  font-size: 21px;
  line-height: 1.5;
  font-weight: 600;
}
.selected-service-main p {
  margin-top: 9px;
  font-size: 12px;
  color: var(--muted);
}
.detail-separator {
  margin: 0 9px;
  color: #b1bbaa;
}
.appointment-facts {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin-top: 21px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--muted);
}
.appointment-facts > span {
  display: flex;
  align-items: flex-start;
  gap: 9px;
}
.appointment-facts .el-icon {
  margin-top: 3px;
  font-size: 15px;
  color: var(--brand);
  flex-shrink: 0;
}
.selection-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 25px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}
.selection-actions .el-button + .el-button {
  margin-left: 0;
}
.selection-actions .el-button span + span {
  margin-left: 6px;
}
.qualified-count {
  font-size: 12px;
  color: var(--brand);
  white-space: nowrap;
}
.candidate-list {
  padding: 2px 21px 10px;
}
.candidate-card {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 11px;
  margin-bottom: 13px;
}
.candidate-card.recommended {
  border-color: #cbdcc7;
  background: #fbfcf8;
}
.candidate-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
}
.candidate-person {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.candidate-name {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.candidate-person p {
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}
.recommended-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--brand);
  background: #e8f1e5;
  border-radius: 4px;
  padding: 3px 5px;
}
.match-score {
  text-align: right;
  flex-shrink: 0;
}
.match-score strong {
  display: block;
  font-size: 28px;
  font-weight: 550;
  line-height: 1.1;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}
.match-score span {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  color: var(--muted);
}
.candidate-facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 20px 0 17px;
}
.candidate-facts > span {
  display: flex;
  gap: 7px;
  align-items: center;
  font-size: 12px;
  color: #566851;
}
.candidate-facts .el-icon {
  color: #8ca283;
  font-size: 14px;
}
.score-bars {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 0 0 16px;
}
.score-bar-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.score-bar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--muted);
}
.score-bar-head .stat-number {
  color: var(--ink);
  font-weight: 550;
}
.score-bar-track {
  height: 5px;
  border-radius: 999px;
  background: #eaeee5;
  overflow: hidden;
}
.score-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
  transition: width 240ms ease;
}
.candidate-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 15px;
  border-top: 1px solid #e8eee2;
}
.candidate-bottom > span {
  font-size: 12px;
  color: var(--muted);
}
.candidate-bottom .el-button .el-icon {
  margin-left: 10px;
}
.small-divider {
  margin: 0 7px;
  color: #a6b2a1;
}
.excluded-list {
  border-bottom: 0;
  border-top-color: var(--line);
  margin: 0 22px;
}
.excluded-list :deep(.el-collapse-item__header) {
  color: var(--muted);
  font-size: 12px;
}
.excluded-person {
  padding: 10px 0;
  display: flex;
  align-items: flex-start;
  gap: 20px;
}
.excluded-person > span {
  min-width: 75px;
  color: var(--ink);
  font-size: 12px;
}
.excluded-person p {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.dispatch-footnote {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin: 18px 4px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
.dispatch-footnote .el-icon {
  margin-top: 3px;
  flex-shrink: 0;
}
.select-placeholder {
  min-height: 430px;
}
.assign-context {
  margin-bottom: 23px;
  color: var(--muted);
  font-size: 13px;
}
.assign-note {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}
@media (max-width: 1100px) {
  .dispatch-layout {
    grid-template-columns: 1fr;
  }
  .queue-body {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px 8px;
  }
  .queue-body > .empty-state {
    grid-column: 1 / -1;
  }
  .queue-filters {
    max-width: 360px;
  }
}
@media (max-width: 520px) {
  .queue-body {
    display: block;
  }
  .selected-service {
    padding: 22px 19px;
  }
  .candidate-list {
    padding-inline: 15px;
  }
  .candidate-card {
    padding: 16px;
  }
  .candidate-facts {
    grid-template-columns: 1fr;
    gap: 11px;
  }
  .score-bars {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .selected-service-main h2 {
    font-size: 19px;
  }
  .order-reference {
    display: none;
  }
}
</style>
