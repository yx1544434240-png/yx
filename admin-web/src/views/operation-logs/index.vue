<template>
  <section>
    <PageHeader eyebrow="审计记录" title="操作日志" description="查看管理员已完成的操作与审计记录">
      <template #actions>
        <el-button :loading="list.loading.value" @click="list.load">刷新记录</el-button>
      </template>
    </PageHeader>
    <div class="panel">
      <DataTable
        :rows="list.rows.value"
        :loading="list.loading.value"
        :error="list.error.value"
        :total="list.total.value"
        :page-num="list.pageNum.value"
        :page-size="list.pageSize.value"
        :page-sizes="[]"
        empty-text="暂无管理操作记录"
        error-text="操作日志暂时未能加载"
        @update:page-num="list.setPage"
        @retry="list.load"
      >
        <el-table-column prop="created_at" label="操作时间" min-width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ row.created_at || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator_name" label="操作人" min-width="120">
          <template #default="{ row }">
            <span class="operator-cell">{{ row.operator_name || '系统' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作动作" min-width="240">
          <template #default="{ row }">
            <div class="action-cell">
              <div class="action-title-row">
                <el-tag :type="actionTagType(row.action)" size="small" effect="light" class="action-tag">
                  {{ row.action_text || formatAction(row.action) }}
                </el-tag>
              </div>
              <div
                v-if="row.action && (row.action_text || formatAction(row.action)) !== row.action"
                class="action-code"
                :title="row.action"
              >
                {{ row.action }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作对象" min-width="170">
          <template #default="{ row }">
            <div v-if="row.target_type || row.target_id" class="target-cell">
              <el-tag size="small" type="info" effect="plain" class="target-tag">
                {{ row.target_type_text || formatTargetType(row.target_type) }}
              </el-tag>
              <span v-if="row.target_id" class="target-id">#{{ row.target_id }}</span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="details" label="操作详情" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="details-text">{{ formatDetails(row.details) }}</span>
          </template>
        </el-table-column>
      </DataTable>
    </div>
  </section>
</template>
<script setup>
import PageHeader from '../../components/PageHeader.vue'
import DataTable from '../../components/DataTable.vue'
import request from '../../utils/request'
import { usePagedQuery } from '../../composables/usePagedQuery.js'
import { formatAction, formatTargetType, formatDetails, actionTagType } from '../../utils/presentation.js'

const list = usePagedQuery({
  fetcher: (params, { signal }) => request.get('/operation-logs/page', { params, signal }),
  defaults: {}
})
</script>
<style scoped>
.panel {
  padding: 24px;
}
.time-cell {
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}
.operator-cell {
  font-weight: 500;
  color: var(--ink);
}
.action-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.action-title-row {
  display: flex;
  align-items: center;
}
.action-tag {
  font-weight: 500;
  font-size: 12px;
}
.action-code {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 11px;
  color: var(--muted);
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.target-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.target-tag {
  font-size: 12px;
}
.target-id {
  font-family: var(--el-font-family-monospace, monospace);
  font-weight: 600;
  color: var(--brand);
  font-size: 13px;
}
.details-text {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
  color: var(--ink);
}
.text-muted {
  color: var(--muted);
}
</style>
