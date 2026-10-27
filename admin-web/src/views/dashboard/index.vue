<template>
  <section class="dashboard-page">
    <PageHeader
      :eyebrow="`${COMMUNITY_NAME} · 服务运营`"
      :title="`${greeting}，${currentName}`"
      description="把每一项服务安排妥当，让每一份需要及时得到回应。"
    >
      <template #actions>
        <el-button :loading="refreshing" @click="refreshDashboard">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
        <el-button type="primary" @click="router.push('/dispatch')">
          安排服务
          <el-icon class="action-arrow"><ArrowRight /></el-icon>
        </el-button>
      </template>
    </PageHeader>
    <el-alert
      v-if="loadError"
      title="部分信息暂时未能加载，请点击刷新重试。"
      type="warning"
      :closable="false"
      class="load-alert"
    />
    <div class="overview-grid">
      <article
        v-for="metric in metrics"
        :key="metric.key"
        class="overview-card"
        :class="{ 'featured-metric': metric.key === 'waitingDispatchOrders' }"
      >
        <div class="metric-top">
          <span>{{ metric.label }}</span>
          <el-icon><component :is="metric.icon" /></el-icon>
        </div>
        <div class="metric-value stat-number">
          {{ stat ? Number(stat[metric.key] || 0).toLocaleString() : '—' }}
          <span>笔</span>
        </div>
        <div class="metric-note">{{ metric.note }}</div>
      </article>
    </div>
    <div class="workbench-grid">
      <section class="panel priority-panel">
        <div class="panel-heading">
          <div>
            <h2>
              需要安排的服务
              <span class="heading-count">{{ stat ? stat.waitingDispatchOrders || 0 : '—' }}</span>
            </h2>
            <p>及时处理等待中的预约与需要人工协调的服务</p>
          </div>
          <el-button text type="primary" @click="router.push('/dispatch')">
            查看全部
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div v-if="refreshing && !stat" class="queue-loading"><el-skeleton :rows="4" animated /></div>
        <div v-else-if="waitingOrders.length" class="priority-list">
          <button
            v-for="item in waitingOrders"
            :key="item.order.id"
            class="priority-row"
            @click="router.push({ path: '/orders', query: { orderId: item.order.id } })"
          >
            <span class="service-symbol">
              <el-icon><Service /></el-icon>
            </span>
            <span class="priority-info">
              <strong>{{ item.order.serviceNameSnapshot }}</strong>
              <span>
                {{ item.elder?.name || '服务对象' }}
                <i></i>
                {{ shortDate(item.order.plannedStartAt) }}
              </span>
            </span>
            <span class="status-label" :class="item.order.status === 'WAIT_MANUAL' ? 'is-amber' : ''">
              <span class="status-dot"></span>
              {{ orderStatusText[item.order.status] }}
            </span>
            <el-icon class="row-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
        <div v-else class="empty-state">
          <el-icon><CircleCheck /></el-icon>
          <strong>{{ queueError ? '服务列表暂时无法加载' : '待安排的服务已处理妥当' }}</strong>
          <p>{{ queueError ? '请稍后刷新，或前往订单页面查看。' : '新的预约会在这里展示，随时掌握服务需要。' }}</p>
        </div>
      </section>
      <section class="community-panel">
        <div class="community-panel-top">
          <span class="eyebrow">社区服务概况</span>
          <el-icon><House /></el-icon>
        </div>
        <h2>
          熟悉的社区，
          <br />
          安心的照护。
        </h2>
        <div class="community-numbers">
          <div>
            <strong class="stat-number">{{ stat ? stat.totalElders || 0 : '—' }}</strong>
            <span>位登记老人</span>
          </div>
          <div>
            <strong class="stat-number">{{ stat ? stat.totalStaff || 0 : '—' }}</strong>
            <span>位在册服务人员</span>
          </div>
        </div>
        <div class="community-links">
          <router-link to="/elders">
            查看老人档案
            <el-icon><ArrowRight /></el-icon>
          </router-link>
          <router-link to="/staff">
            管理服务队伍
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
      </section>
    </div>
    <div ref="insightsGridRef" class="insights-grid">
      <section class="panel trend-panel">
        <div class="panel-heading">
          <div>
            <h2>服务需求趋势</h2>
            <p>近 7 天新增订单数量</p>
          </div>
          <span class="period-label">最近 7 天</span>
        </div>
        <div
          v-if="stat?.recentOrdersTrend?.length"
          ref="lineChartRef"
          class="chart-area"
          role="img"
          aria-label="近七天订单趋势折线图"
        ></div>
        <div v-else class="empty-state compact-empty">
          <el-icon><TrendCharts /></el-icon>
          <p>暂无订单趋势数据</p>
        </div>
      </section>
      <section class="panel distribution-panel">
        <div class="panel-heading">
          <div>
            <h2>大家需要的服务</h2>
            <p>按服务项目统计订单分布</p>
          </div>
        </div>
        <div
          v-if="stat?.categoryDistribution?.length"
          ref="pieChartRef"
          class="chart-area"
          role="img"
          aria-label="服务项目需求分布环形图"
        ></div>
        <div v-else class="empty-state compact-empty">
          <el-icon><ShoppingBag /></el-icon>
          <p>有服务预约后，这里将显示需求分布</p>
        </div>
      </section>
    </div>
    <section class="panel team-panel">
      <div class="panel-heading">
        <div>
          <h2>社区服务队伍</h2>
          <p>查看人员状态、累计服务与评价</p>
        </div>
        <el-button text type="primary" @click="router.push('/staff')">
          管理人员
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div class="team-table-wrap">
        <el-table :data="stat?.staffWorkloadList || []" empty-text="暂无服务人员信息">
          <el-table-column label="服务人员" min-width="160">
            <template #default="{ row }">
              <div class="team-person">
                <span class="person-avatar">{{ (row.staffName || '员').slice(-2) }}</span>
                <span>{{ row.staffName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="工作状态" min-width="130">
            <template #default="{ row }">
              <span
                class="status-label"
                :class="row.status === 'AVAILABLE' ? 'is-green' : row.status === 'BUSY' ? 'is-amber' : ''"
              >
                <span class="status-dot"></span>
                {{ row.status === 'AVAILABLE' ? '可接单' : row.status === 'BUSY' ? '服务中' : '休息中' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="累计完成" min-width="130">
            <template #default="{ row }">
              {{ row.totalOrders || 0 }}
              <span class="cell-unit">单</span>
            </template>
          </el-table-column>
          <el-table-column label="服务评分" min-width="150">
            <template #default="{ row }">
              <span class="rating-value">
                {{ row.rating == null ? '暂无评分' : Number(row.rating).toFixed(1) }}
                <span v-if="row.rating != null" class="rating-unit">/ 5.0</span>
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </section>
</template>

<script setup>
import PageHeader from '../../components/PageHeader.vue'
import { COMMUNITY_NAME } from '../../utils/app-config.js'
import { getUser } from '../../utils/admin-session.js'
import { escapeHtml } from '../../utils/escape.js'
import { computed, ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { use, init, graphic } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import request from '../../utils/request'
import { orderStatusText, shortDate } from '../../utils/presentation'
use([LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
const router = useRouter()
const stat = ref(null)
const waitingOrders = ref([])
const refreshing = ref(false)
const loadError = ref(false)
const queueError = ref(false)
const insightsGridRef = ref(null)
const lineChartRef = ref(null)
const pieChartRef = ref(null)
let lineChart, pieChart, resizeObserver, pollTimer
const currentName = computed(() => {
  const user = getUser()
  return user?.realName || user?.username || '管理员'
})
// 卸载后不再写状态或重建已 dispose 的图表（R4）
let disposed = false
const hour = Number(
  new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: '2-digit', hourCycle: 'h23' }).format(new Date())
)
const greeting = hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
const metrics = [
  { key: 'totalOrders', label: '累计服务订单', icon: 'Document', note: '社区服务预约总量' },
  { key: 'waitingDispatchOrders', label: '等待安排', icon: 'Connection', note: '待派单与需要人工协调的服务' },
  { key: 'inServiceOrders', label: '正在履约', icon: 'Timer', note: '已接单待上门及服务中的订单' },
  { key: 'completedOrders', label: '已完成服务', icon: 'CircleCheck', note: '已经确认完成的服务订单' }
]
async function refreshDashboard() {
  if (refreshing.value) return
  refreshing.value = true
  const [summary, queue] = await Promise.allSettled([
    request.get('/stats/dashboard', { silent: true }),
    request.get('/orders/page', {
      params: { pageNum: 1, pageSize: 4, status: 'WAIT_MANUAL,WAIT_DISPATCH' },
      silent: true
    })
  ])
  if (disposed) return
  loadError.value = summary.status === 'rejected' || queue.status === 'rejected'
  queueError.value = queue.status === 'rejected'
  if (summary.status === 'fulfilled') stat.value = summary.value || {}
  if (queue.status === 'fulfilled') waitingOrders.value = queue.value?.records || []
  await nextTick()
  if (disposed) return
  renderCharts()
  refreshing.value = false
}
function renderCharts() {
  const fontFamily = 'PingFang SC, Microsoft YaHei, sans-serif'
  if (lineChartRef.value) {
    if (lineChart && lineChart.getDom() !== lineChartRef.value) {
      lineChart.dispose()
      lineChart = null
    }
    lineChart ||= init(lineChartRef.value)
    const trend = stat.value?.recentOrdersTrend || []
    lineChart.setOption({
      textStyle: { fontFamily },
      tooltip: { trigger: 'axis', backgroundColor: '#fff', borderColor: '#e3e9e2', textStyle: { color: '#24342e' } },
      grid: { left: 12, right: 24, top: 25, bottom: 12, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trend.map(item => item.date),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#64716a', margin: 16, fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#edf0e9', type: 'dashed' } },
        axisLabel: { color: '#64716a', fontSize: 11 }
      },
      series: [
        {
          name: '新增订单',
          type: 'line',
          smooth: 0.25,
          symbol: 'circle',
          symbolSize: 6,
          showSymbol: false,
          lineStyle: { color: '#176b5d', width: 2.5 },
          itemStyle: { color: '#176b5d' },
          areaStyle: {
            color: new graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#176b5d20' },
              { offset: 1, color: '#176b5d01' }
            ])
          },
          data: trend.map(item => Number(item.count))
        }
      ]
    })
  } else {
    lineChart?.dispose()
    lineChart = null
  }
  if (pieChartRef.value) {
    if (pieChart && pieChart.getDom() !== pieChartRef.value) {
      pieChart.dispose()
      pieChart = null
    }
    pieChart ||= init(pieChartRef.value)
    pieChart.setOption({
      textStyle: { fontFamily },
      color: ['#176b5d', '#7da690', '#c1cda4', '#ba8b57', '#9bad9f', '#d5d9cd'],
      tooltip: {
        trigger: 'item',
        formatter: params => `${escapeHtml(params.name)}<br/>${Number(params.value) || 0} 笔 · ${params.percent ?? 0}%`
      },
      legend: {
        bottom: 10,
        left: 'center',
        itemWidth: 8,
        itemHeight: 8,
        icon: 'circle',
        textStyle: { color: '#64716a', fontSize: 11 },
        type: 'scroll'
      },
      series: [
        {
          name: '服务需求',
          type: 'pie',
          center: ['50%', '43%'],
          radius: ['49%', '67%'],
          itemStyle: { borderWidth: 4, borderColor: '#fff', borderRadius: 4 },
          label: { show: false },
          emphasis: {
            label: {
              show: true,
              position: 'center',
              formatter: '{b}\n{d}%',
              fontSize: 13,
              lineHeight: 23,
              color: '#24342e'
            }
          },
          data: stat.value?.categoryDistribution || []
        }
      ]
    })
  } else {
    pieChart?.dispose()
    pieChart = null
  }
  resizeCharts()
}
function resizeCharts() {
  lineChart?.resize()
  pieChart?.resize()
}
function onVisibilityChange() {
  if (typeof document !== 'undefined' && document.visibilityState === 'visible' && !disposed) {
    refreshDashboard()
  }
}
onMounted(() => {
  refreshDashboard()
  resizeObserver = new ResizeObserver(resizeCharts)
  if (insightsGridRef.value) resizeObserver.observe(insightsGridRef.value)
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibilityChange)
    pollTimer = setInterval(() => {
      if (document.visibilityState === 'visible') refreshDashboard()
    }, 30000)
  }
})
onBeforeUnmount(() => {
  disposed = true
  clearInterval(pollTimer)
  if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisibilityChange)
  resizeObserver?.disconnect()
  lineChart?.dispose()
  pieChart?.dispose()
})
</script>

<style scoped>
.load-alert {
  margin-bottom: 20px;
}
.action-arrow {
  margin-left: 10px;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 24px;
}
.overview-card {
  padding: 22px 24px 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}
.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}
.metric-top .el-icon {
  font-size: 19px;
  color: #8ea58e;
}
.metric-value {
  margin: 15px 0 10px;
  font-size: 35px;
  font-weight: 600;
  line-height: 1.2;
}
.metric-value > span {
  margin-left: 8px;
  font-size: 12px;
  color: var(--muted);
  font-weight: 400;
}
.metric-note {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
}
.featured-metric {
  background: #edf4ed;
  border-color: #d8e5d6;
}
.featured-metric .metric-value {
  color: var(--brand);
}
.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(280px, 1fr);
  gap: 22px;
  margin-bottom: 24px;
}
.heading-count {
  display: inline-block;
  margin-left: 7px;
  padding: 1px 7px;
  border-radius: 5px;
  background: #f2f5ee;
  color: var(--brand);
  font-size: 12px;
}
.priority-list {
  padding: 0 24px 12px;
}
.priority-row {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 13px;
  border: 0;
  border-bottom: 1px solid #edf0e9;
  padding: 18px 0;
  background: none;
  text-align: left;
  cursor: pointer;
  color: var(--ink);
}
.priority-row:last-child {
  border-bottom: 0;
}
.priority-row:hover .priority-info strong {
  color: var(--brand);
}
.service-symbol {
  width: 38px;
  height: 41px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: #f2f4e9;
  border-radius: 10px;
  color: #768449;
  font-size: 20px;
}
.priority-info {
  flex: 1;
  min-width: 0;
}
.priority-info strong {
  display: block;
  font-size: 13px;
  font-weight: 550;
  line-height: 1.6;
}
.priority-info > span {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
  margin-top: 4px;
}
.priority-info i {
  height: 3px;
  width: 3px;
  background: #a4af9e;
  border-radius: 50%;
}
.row-arrow {
  color: #93a18e;
  font-size: 13px;
}
.queue-loading {
  padding: 24px;
}
.community-panel {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  padding: 27px 28px;
  background: #e7eee1;
  border: 1px solid #dce5d5;
}
.community-panel::after {
  content: '';
  position: absolute;
  width: 170px;
  height: 170px;
  border: 1px solid #cedbc4;
  border-radius: 50%;
  right: -90px;
  top: 28px;
  pointer-events: none;
}
.community-panel-top {
  display: flex;
  justify-content: space-between;
  color: var(--brand);
}
.community-panel-top > .el-icon {
  font-size: 23px;
}
.community-panel h2 {
  font-size: 24px;
  font-weight: 550;
  line-height: 1.65;
  color: #2e4e37;
  letter-spacing: -0.6px;
  margin: 5px 0 25px;
}
.community-numbers {
  display: flex;
  gap: 36px;
}
.community-numbers strong {
  display: block;
  font-size: 28px;
  font-weight: 550;
  color: #34543a;
}
.community-numbers span {
  display: block;
  margin-top: 6px;
  color: #596b55;
  font-size: 12px;
}
.community-links {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding-top: 25px;
  margin-top: 23px;
  border-top: 1px solid #cedcc5;
}
.community-links a {
  display: inline-flex;
  gap: 10px;
  align-items: center;
  font-size: 12px;
}
.insights-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(300px, 1fr);
  gap: 22px;
  margin-bottom: 24px;
}
.chart-area {
  height: 260px;
  margin: 0 18px 15px;
}
.period-label {
  font-size: 12px;
  color: var(--muted);
  padding: 5px 9px;
  border: 1px solid var(--line);
  border-radius: 5px;
}
.compact-empty {
  min-height: 260px;
}
.team-table-wrap {
  padding: 0 24px 15px;
}
.team-person {
  display: flex;
  align-items: center;
  gap: 13px;
  font-size: 13px;
}
.team-person .person-avatar {
  width: 32px;
  height: 34px;
  font-size: 12px;
}
.cell-unit,
.rating-unit {
  font-size: 12px;
  color: var(--muted);
}
.rating-value {
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1150px) {
  .overview-card {
    padding: 20px;
  }
  .metric-top {
    font-size: 12px;
  }
  .workbench-grid {
    grid-template-columns: minmax(0, 1.6fr) minmax(250px, 1fr);
  }
  .community-panel {
    padding: 25px 21px;
  }
  .community-numbers {
    gap: 24px;
  }
}
@media (max-width: 980px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .workbench-grid,
  .insights-grid {
    grid-template-columns: 1fr;
  }
  .community-panel h2 br {
    display: none;
  }
  .community-numbers {
    gap: 50px;
  }
  .community-links {
    margin-top: 17px;
    padding-top: 18px;
  }
}
@media (max-width: 520px) {
  .overview-grid {
    gap: 12px;
  }
  .overview-card {
    padding: 17px;
  }
  .metric-value {
    font-size: 30px;
  }
  .metric-note {
    font-size: 12px;
  }
  .priority-list {
    padding-inline: 18px;
  }
  .priority-row {
    gap: 10px;
  }
  .priority-row .row-arrow {
    display: none;
  }
  .priority-row .status-label {
    font-size: 12px;
    padding: 4px 7px;
  }
  .team-table-wrap {
    padding-inline: 15px;
  }
}
</style>
