<template>
  <el-dialog :model-value="visible" title="工作人员入驻审核" width="min(1000px, 96vw)" @close="emit('close')">
    <el-alert v-if="failed" title="申请加载失败，请重试" type="error" :closable="false">
      <el-button link @click="load">重新加载</el-button>
    </el-alert>
    <el-select v-model="status" style="width: 180px; margin-bottom: 16px" :disabled="busy" @change="search">
      <el-option label="待审核" value="PENDING" />
      <el-option label="已通过" value="APPROVED" />
      <el-option label="已退回" value="REJECTED" />
    </el-select>
    <el-table v-loading="loading" :data="rows" :empty-text="failed ? '加载失败' : '暂无此类入驻申请'">
      <el-table-column prop="realName" label="申请人" min-width="110" />
      <el-table-column label="手机号码" min-width="170">
        <template #default="{ row }"><MaskedText :value="row.phone" /></template>
      </el-table-column>
      <el-table-column prop="serviceArea" label="服务区域" min-width="160" />
      <el-table-column prop="submittedAt" label="提交时间" min-width="170" />
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="busy" @click="select(row)">查看申请</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next, total"
      :disabled="busy"
      @current-change="load"
    />
    <el-dialog
      v-model="detailVisible"
      title="核验入驻资料"
      width="min(680px, 94vw)"
      append-to-body
      :close-on-click-modal="!busy"
      :close-on-press-escape="!busy"
      :show-close="!busy"
    >
      <template v-if="selected">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="申请人">{{ selected.realName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话"><MaskedText :value="selected.phone" /></el-descriptions-item>
          <el-descriptions-item label="服务区域">{{ selected.serviceArea }}</el-descriptions-item>
          <el-descriptions-item label="申请说明">
            <span class="application-copy">{{ selected.introduction }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="selected.auditReason" label="审核意见">
            {{ selected.auditReason }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form v-if="selected.status === 'PENDING'" label-position="top" class="application-form">
          <el-form-item label="审核意见（退回必填）">
            <el-input v-model="reason" type="textarea" maxlength="200" show-word-limit :disabled="busy" />
          </el-form-item>
          <el-alert
            title="通过前核对服务站点；开通后默认为休息，需另行审核技能并安排班次。"
            type="info"
            :closable="false"
          />
          <el-form-item label="出发服务站点">
            <el-input v-model="stationName" maxlength="100" :disabled="busy" />
          </el-form-item>
          <div class="application-coordinates">
            <el-form-item label="站点经度">
              <el-input-number v-model="longitude" :min="-180" :max="180" :precision="6" :disabled="busy" />
            </el-form-item>
            <el-form-item label="站点纬度">
              <el-input-number v-model="latitude" :min="-90" :max="90" :precision="6" :disabled="busy" />
            </el-form-item>
          </div>
        </el-form>
      </template>
      <template #footer>
        <el-button :disabled="busy" @click="detailVisible = false">关闭</el-button>
        <template v-if="selected?.status === 'PENDING'">
          <el-button type="danger" plain :loading="busy" @click="audit(false)">退回补充</el-button>
          <el-button type="primary" :loading="busy" @click="audit(true)">审核通过并开通</el-button>
        </template>
      </template>
    </el-dialog>
  </el-dialog>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'
import MaskedText from './MaskedText.vue'
const props = defineProps({ visible: Boolean })
const emit = defineEmits(['close', 'reviewed'])
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  status = ref('PENDING'),
  loading = ref(false),
  failed = ref(false),
  busy = ref(false)
const detailVisible = ref(false),
  selected = ref(null),
  reason = ref(''),
  stationName = ref(''),
  longitude = ref(null),
  latitude = ref(null)
let sequence = 0,
  disposed = false
async function load() {
  const seq = ++sequence
  loading.value = true
  failed.value = false
  try {
    const result = await request.get('/staff/applications/page', {
      params: { pageNum: page.value, pageSize: 20, status: status.value }
    })
    if (seq !== sequence || disposed) return
    rows.value = result.records || []
    total.value = result.total || 0
    if (!rows.value.length && page.value > 1) {
      page.value = Math.max(1, Math.ceil(total.value / 20))
      return load()
    }
  } catch {
    if (seq === sequence && !disposed) failed.value = true
  } finally {
    if (seq === sequence && !disposed) loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}
function select(row) {
  selected.value = row
  reason.value = ''
  stationName.value = '阳光社区居家养老服务站'
  longitude.value = 116.407396
  latitude.value = 39.904199
  detailVisible.value = true
}
watch(
  () => props.visible,
  value => {
    ++sequence
    if (value) {
      status.value = 'PENDING'
      search()
    } else detailVisible.value = false
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  disposed = true
  ++sequence
})
async function audit(approve) {
  if (busy.value || selected.value?.status !== 'PENDING') return
  if (!approve && !reason.value.trim()) {
    ElMessage.warning('请填写退回原因')
    return
  }
  if (approve && (!stationName.value.trim() || !Number.isFinite(longitude.value) || !Number.isFinite(latitude.value))) {
    ElMessage.warning('请核对服务站点和经纬度')
    return
  }
  const row = selected.value
  busy.value = true
  try {
    await request.post('/staff/applications/audit', {
      id: row.id,
      revision: row.revision,
      approve,
      reason: reason.value.trim(),
      ...(approve
        ? { stationName: stationName.value.trim(), longitude: longitude.value, latitude: latitude.value }
        : {})
    })
    if (disposed) return
    ElMessage.success(approve ? '入驻通过，已开通工作人员身份' : '已退回，申请人可补充资料')
    detailVisible.value = false
    emit('reviewed')
    await load()
  } catch {
  } finally {
    if (!disposed) busy.value = false
  }
}
</script>
<style scoped>
.application-copy {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.application-form {
  margin-top: 20px;
}
.application-coordinates {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}
</style>
