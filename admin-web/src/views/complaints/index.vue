<template>
  <div class="complaints-container">
    <PageHeader
      eyebrow="反馈处理"
      title="听见反馈，及时回应"
      description="跟进服务意见，记录处理结果，让每次反馈有回应。"
    />
    <el-card shadow="never" class="main-card">
      <div class="toolbar-split">
        <div class="toolbar-filters">
          <el-select
            v-model="list.filters.status"
            placeholder="全部状态"
            clearable
            style="width: 160px"
            @change="list.search"
          >
            <el-option v-for="option in complaintStatusOptions" :key="option.value" v-bind="option" />
          </el-select>
          <el-button v-if="list.filters.status" icon="Refresh" @click="list.reset">重置</el-button>
        </div>
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
        empty-text="暂无需要处理的反馈"
        error-text="反馈列表暂时未能加载"
        @update:page-num="list.setPage"
        @update:page-size="list.setPageSize"
        @retry="list.load"
      >
        <el-table-column prop="id" label="编号" width="100" />
        <el-table-column prop="orderId" label="关联订单编号" width="180">
          <template #default="{ row }">
            <span v-if="row.orderId && row.orderId !== '0'">{{ row.orderId }}</span>
            <el-tag v-else size="small" type="info">平台/社区通用建议</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="complaintTitle" label="投诉主题" width="180">
          <template #default="{ row }">
            <span class="cell-title">{{ row.complaintTitle }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="complaintContent" label="反馈详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <StatusTag kind="complaint" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="社区处置结果" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              type="primary"
              link
              :loading="startingId === row.id"
              @click="startHandle(row)"
            >
              受理
            </el-button>
            <el-button
              v-if="['PENDING', 'PROCESSING'].includes(row.status)"
              type="primary"
              link
              @click="openHandleModal(row)"
            >
              处理答复
            </el-button>
            <span v-else class="text-success text-sm">{{ statusText('complaint', row.status) }}</span>
          </template>
        </el-table-column>
      </DataTable>
    </el-card>

    <!-- 处理投诉弹窗 -->
    <el-dialog v-model="handleVisible" title="处理并答复投诉" width="500px">
      <el-form :model="handleForm" label-width="100px">
        <el-form-item label="投诉主题">
          <span>{{ currentComplaint ? currentComplaint.complaintTitle : '' }}</span>
        </el-form-item>
        <el-form-item label="投诉内容">
          <span class="text-muted">{{ currentComplaint ? currentComplaint.complaintContent : '' }}</span>
        </el-form-item>
        <el-form-item label="处理答复">
          <el-input
            v-model="handleForm.result"
            type="textarea"
            :rows="4"
            maxlength="4000"
            show-word-limit
            placeholder="填写社区调查核实结论及对居民的处理答复..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">办结并保存答复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import PageHeader from '../../components/PageHeader.vue'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { statusOptions, statusText } from '../../utils/presentation.js'
import { usePagedQuery } from '../../composables/usePagedQuery.js'

const list = usePagedQuery({
  fetcher: (params, { signal }) => request.get('/fulfillment/complaints/page', { params, signal }),
  defaults: { status: '' }
})
const complaintStatusOptions = statusOptions('complaint').filter(option => option.value !== 'REJECTED')
const handleVisible = ref(false)
const currentComplaint = ref(null)
const startingId = ref(null)
const handling = ref(false)

const handleForm = reactive({
  id: null,
  result: ''
})

const startHandle = async row => {
  if (startingId.value) return
  startingId.value = row.id
  try {
    await request.post('/fulfillment/complaint/start', { id: row.id })
    ElMessage.success('已受理，居民可查看处理进度')
    list.load()
  } catch {
  } finally {
    startingId.value = null
  }
}

const openHandleModal = row => {
  currentComplaint.value = row
  handleForm.id = row.id
  handleForm.result = ''
  handleVisible.value = true
}

const submitHandle = () => {
  if (handling.value) return
  if (!handleForm.result.trim()) {
    ElMessage.warning('请填写处理答复内容')
    return
  }
  handling.value = true
  request
    .post('/fulfillment/complaint/handle', { ...handleForm, result: handleForm.result.trim() })
    .then(() => {
      ElMessage.success('投诉已办结并更新记录')
      handleVisible.value = false
      list.load()
    })
    .finally(() => {
      handling.value = false
    })
}
</script>

<style scoped>
.feedback-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}
.feedback-tools .el-select {
  width: 150px;
}

.header-bar {
  margin-bottom: 16px;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
