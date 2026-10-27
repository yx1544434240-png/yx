<template>
  <el-dialog :model-value="Boolean(skillId)" title="核验资质证明" width="min(760px, 94vw)" @close="emit('close')">
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-alert v-else-if="failed" title="材料加载失败，请关闭后重试" type="error" :closable="false" />
    <template v-else-if="skill">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="工作人员">{{ skill.staffName || skill.staffId }}</el-descriptions-item>
        <el-descriptions-item label="资质名称">{{ skill.skillName }}</el-descriptions-item>
        <el-descriptions-item label="证书编号">{{ skill.certificateNo || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          {{ statuses[skill.auditStatus] || skill.auditStatus }}
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ skill.submittedAt || '管理员直接录入' }}</el-descriptions-item>
        <el-descriptions-item v-if="skill.auditReason" label="审核意见">{{ skill.auditReason }}</el-descriptions-item>
      </el-descriptions>
      <div class="credential-materials">
        <PrivatePhoto v-for="source in proofs" :key="source" :source="source" :width="160" :height="120" />
        <el-empty v-if="!proofs.length" description="历史录入记录，没有上传证明图片" :image-size="60" />
      </div>
      <el-form v-if="skill.auditStatus === 'PENDING'" label-position="top">
        <el-form-item label="审核意见（退回时必填）">
          <el-input
            v-model="reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请核对姓名、证书编号和材料清晰度；退回时说明需要补充什么"
            :disabled="submitting"
          />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button :disabled="submitting" @click="emit('close')">关闭</el-button>
      <template v-if="skill?.auditStatus === 'PENDING' && !failed && !loading">
        <el-button type="danger" plain :loading="submitting" @click="audit(false)">退回补充</el-button>
        <el-button type="primary" :loading="submitting" @click="audit(true)">审核通过</el-button>
      </template>
    </template>
  </el-dialog>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'
import PrivatePhoto from './PrivatePhoto.vue'
const props = defineProps({ skillId: { type: [String, Number], default: null } })
const emit = defineEmits(['close', 'reviewed'])
const skill = ref(null),
  reason = ref(''),
  loading = ref(false),
  failed = ref(false),
  submitting = ref(false)
const statuses = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已退回' }
const proofs = computed(() => {
  try {
    const list = JSON.parse(skill.value?.proofUrlsJson || '[]')
    return Array.isArray(list) ? list.slice(0, 3) : []
  } catch {
    return []
  }
})
let sequence = 0
watch(
  () => props.skillId,
  async id => {
    const current = ++sequence
    skill.value = null
    reason.value = ''
    failed.value = false
    if (!id) return
    loading.value = true
    try {
      const row = await request.get(`/staff/credentials/${id}`)
      if (current === sequence) skill.value = row
    } catch {
      if (current === sequence) failed.value = true
    } finally {
      if (current === sequence) loading.value = false
    }
  },
  { immediate: true }
)
async function audit(approve) {
  if (submitting.value || !skill.value) return
  if (!approve && !reason.value.trim()) {
    ElMessage.warning('请填写退回原因，方便工作人员补充材料')
    return
  }
  const id = props.skillId
  const current = sequence
  submitting.value = true
  try {
    await request.post('/staff/credentials/audit', {
      id,
      revision: skill.value.revision,
      approve,
      reason: reason.value.trim()
    })
    ElMessage.success(approve ? '资质审核通过' : '已退回，工作人员可查看原因并补充材料')
    emit('reviewed')
    if (current === sequence) emit('close')
  } catch {
  } finally {
    submitting.value = false
  }
}
</script>
<style scoped>
.credential-materials {
  display: flex;
  flex-wrap: wrap;
  margin: 20px 0;
  gap: 12px;
}
</style>
