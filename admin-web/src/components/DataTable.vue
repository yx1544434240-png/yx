<template>
  <div class="data-table">
    <el-skeleton :loading="loading && !rows.length && !error" animated :rows="skeletonRows">
      <template #default>
        <el-table v-loading="loading && rows.length > 0" :data="rows" stripe style="width: 100%" v-bind="$attrs">
          <slot />
          <template #empty>
            <div v-if="error" class="empty-state table-state" role="alert">
              <strong>{{ errorText }}</strong>
              <p>请检查网络后重试。</p>
              <el-button type="primary" plain @click="$emit('retry')">重新加载</el-button>
            </div>
            <div v-else class="empty-state table-state">
              <strong>{{ emptyText }}</strong>
              <p v-if="emptyHint">{{ emptyHint }}</p>
            </div>
          </template>
        </el-table>
      </template>
    </el-skeleton>
    <div v-if="total > 0" class="pagination-box">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="pageSizes"
        :layout="pageSizes.length ? 'total, sizes, prev, pager, next, jumper' : 'total, prev, pager, next'"
        @current-change="$emit('update:pageNum', $event)"
        @size-change="$emit('update:pageSize', $event)"
      />
    </div>
  </div>
</template>

<script setup>
// 列表通用壳：骨架屏 / 翻页加载 / 空态 / 错误态 / 分页（C1、C5）
defineOptions({ inheritAttrs: false })
defineProps({
  rows: { type: Array, required: true },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  pageSizes: { type: Array, default: () => [10, 20, 50] },
  emptyText: { type: String, default: '暂无数据' },
  emptyHint: { type: String, default: '' },
  errorText: { type: String, default: '暂时未能加载' },
  skeletonRows: { type: Number, default: 6 }
})
defineEmits(['update:pageNum', 'update:pageSize', 'retry'])
</script>

<style scoped>
.table-state {
  min-height: 180px;
  line-height: 1.6;
}
</style>
