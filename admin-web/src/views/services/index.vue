<template>
  <div class="services-container">
    <PageHeader
      eyebrow="服务项目"
      title="把日常所需，送到家门口"
      description="维护社区服务项目、价格、服务范围与预约说明。"
    />
    <el-card shadow="never" class="main-card">
      <div class="toolbar-split">
        <el-form :inline="true" :model="list.filters" class="toolbar-filters" @submit.prevent="list.search">
          <el-form-item label="服务类别">
            <el-select
              v-model="list.filters.category"
              placeholder="全部类别"
              clearable
              style="width: 150px"
              @change="list.search"
            >
              <el-option v-for="option in categoryOptions" :key="option.value" v-bind="option" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="list.filters.status"
              placeholder="全部状态"
              clearable
              style="width: 140px"
              @change="list.search"
            >
              <el-option v-for="option in serviceStatusOptions" :key="option.value" v-bind="option" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="list.filters.category || list.filters.status">
            <el-button icon="Refresh" @click="list.reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="toolbar-actions">
          <el-button type="primary" icon="Plus" @click="openModal(null)">新增服务项目</el-button>
        </div>
      </div>

      <DataTable
        :rows="list.rows.value"
        :loading="list.loading.value"
        :error="list.error.value"
        :total="list.total.value"
        :page-num="list.pageNum.value"
        :page-size="list.pageSize.value"
        empty-text="还没有服务项目"
        empty-hint="点击右上角「新增服务项目」开始配置。"
        error-text="服务项目暂时未能加载"
        @update:page-num="list.setPage"
        @update:page-size="list.setPageSize"
        @retry="list.load"
      >
        <el-table-column label="服务图片" width="130">
          <template #default="{ row }">
            <img
              class="service-thumbnail"
              :src="imageFor(row.icon, row.category)"
              alt="服务展示图"
              @error="useDefaultImage($event, row.category)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="项目名称" width="160">
          <template #default="{ row }">
            <span class="cell-title">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="服务类别" width="130">
          <template #default="{ row }">
            <StatusTag kind="category" :value="row.category" />
          </template>
        </el-table-column>
        <el-table-column prop="price" label="基准单价" width="120">
          <template #default="{ row }">
            <span class="text-warm text-strong">¥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMinutes" label="预计时长" width="120">
          <template #default="{ row }">{{ row.durationMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column prop="requiredSkills" label="必需技能资质" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.requiredSkills" size="small" type="info">
              {{ skillText(row.requiredSkills) }}
            </el-tag>
            <span v-else class="text-muted">无硬性要求</span>
          </template>
        </el-table-column>
        <el-table-column prop="serviceArea" label="覆盖区域" width="140" />
        <el-table-column prop="description" label="服务介绍" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <StatusTag kind="service" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openModal(row)">编辑</el-button>
            <el-button
              link
              :loading="statusAction.pendingKey.value === row.id"
              :disabled="statusAction.pendingKey.value !== null && statusAction.pendingKey.value !== row.id"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ON_SHELF' ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </DataTable>
    </el-card>

    <!-- 项目编辑弹窗 -->
    <el-dialog
      v-model="modalVisible"
      :title="currentItem.id ? '编辑服务项目' : '新增服务项目'"
      width="550px"
      @closed="itemFormRef?.clearValidate()"
    >
      <el-form ref="itemFormRef" :model="currentItem" :rules="itemRules" label-width="110px">
        <el-form-item label="服务名称" prop="name">
          <el-input v-model="currentItem.name" placeholder="如：暖心送餐服务" />
        </el-form-item>
        <el-form-item label="服务类别" prop="category">
          <el-select v-model="currentItem.category" style="width: 100%">
            <el-option v-for="option in categoryOptions" :key="option.value" v-bind="option" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务展示图">
          <div class="image-editor">
            <img
              class="cover-preview"
              :src="imageFor(currentItem.icon, currentItem.category)"
              alt="当前服务展示图"
              @error="useDefaultImage($event, currentItem.category)"
            />
            <div class="image-actions">
              <el-upload
                accept="image/jpeg,image/png"
                :show-file-list="false"
                :disabled="uploading || saving"
                :http-request="uploadImage"
              >
                <el-button :loading="uploading" :disabled="uploading || saving">
                  {{ uploading ? '正在上传…' : '上传替换图片' }}
                </el-button>
              </el-upload>
              <el-button text :disabled="uploading || saving" @click="currentItem.icon = ''">恢复分类默认图</el-button>
              <p>JPG / PNG，最大2MB。上传后点击“保存项目”生效。</p>
              <p>首页分类入口使用该分类排序最靠前的上架项目图片。</p>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="基准单价(元)" prop="price">
          <el-input-number v-model="currentItem.price" :precision="2" :step="5" :min="0" />
        </el-form-item>
        <el-form-item label="预计时长(分钟)">
          <el-input-number v-model="currentItem.durationMinutes" :step="15" :min="15" :max="480" />
        </el-form-item>
        <el-form-item label="所需技能">
          <el-select
            v-model="selectedSkills"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择技能，可输入补充技能"
            style="width: 100%"
          >
            <el-option v-for="(name, code) in skillNames" :key="code" :label="name" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务区域" prop="serviceArea">
          <el-input v-model="currentItem.serviceArea" :placeholder="COMMUNITY_NAME" />
        </el-form-item>
        <el-form-item label="详细介绍" prop="description">
          <el-input v-model="currentItem.description" type="textarea" placeholder="填写服务详细操作内容与说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modalVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="uploading || saving" @click="saveItem">
          保存项目
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { COMMUNITY_NAME } from '../../utils/app-config.js'
import PageHeader from '../../components/PageHeader.vue'
import DataTable from '../../components/DataTable.vue'
import StatusTag from '../../components/StatusTag.vue'
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { SKILLS, skillText, statusOptions } from '../../utils/presentation.js'
import { usePagedQuery } from '../../composables/usePagedQuery.js'
import { useRowSubmit } from '../../composables/useSubmit.js'
import { confirmDanger } from '../../utils/confirm.js'
import { maxLength, required } from '../../utils/validators.js'

const list = usePagedQuery({
  fetcher: (params, { signal }) => request.get('/service-items/page', { params, signal }),
  defaults: { category: '', status: '' }
})
const categoryOptions = statusOptions('category')
const serviceStatusOptions = statusOptions('service')
const skillNames = SKILLS
const itemFormRef = ref(null)
const itemRules = {
  name: [required('请填写服务名称'), maxLength(64, '服务名称')],
  category: [required('请选择服务类别', 'change')],
  price: [
    {
      trigger: 'change',
      validator: (_rule, value, callback) => (Number(value) > 0 ? callback() : callback(new Error('基准单价需大于 0')))
    }
  ],
  serviceArea: [required('请填写服务区域')],
  description: [maxLength(2000, '详细介绍')]
}
const modalVisible = ref(false)
const uploading = ref(false)
const saving = ref(false)
const editorVersion = ref(0)

// 新增/重置共用一份默认值（C3）
const emptyItem = () => ({
  id: null,
  name: '',
  category: 'MEAL',
  price: 20.0,
  durationMinutes: 60,
  requiredSkills: '',
  serviceArea: COMMUNITY_NAME,
  description: '',
  icon: ''
})
const currentItem = reactive(emptyItem())
const selectedSkills = computed({
  get: () =>
    (currentItem.requiredSkills || '')
      .split(',')
      .map(code => code.trim())
      .filter(Boolean),
  set: value => {
    currentItem.requiredSkills = value.join(',')
  }
})

const openModal = row => {
  editorVersion.value++
  Object.assign(currentItem, emptyItem(), row ? { ...row, icon: row.icon || '' } : {})
  modalVisible.value = true
}

const imageNames = { MEAL: 'meal', CLEANING: 'cleaning', ERRAND: 'errand', ESCORT: 'escort' }
const defaultImage = category => `/images/services/${imageNames[category] || 'meal'}.jpg`
const imageFor = (icon, category) =>
  /^\/media\/service-images\/[a-zA-Z0-9-]+\.(jpg|png|jpeg)$/.test(icon || '') ? '/api' + icon : defaultImage(category)
function useDefaultImage(event, category) {
  const next = new URL(defaultImage(category), window.location.origin).href
  if (event.target.src !== next) event.target.src = next
}
async function uploadImage(options) {
  if (uploading.value) return
  const file = options.file
  if (!['image/jpeg', 'image/png'].includes(file.type) || !file.size || file.size > 2 * 1024 * 1024) {
    ElMessage.warning('请选择不超过2MB的JPG或PNG图片')
    throw new Error('Invalid image')
  }
  uploading.value = true
  const editingVersion = editorVersion.value
  try {
    const form = new FormData()
    form.append('file', file)
    const path = await request.post('/service-items/image', form)
    if (modalVisible.value && editorVersion.value === editingVersion) currentItem.icon = path
    return path
  } finally {
    uploading.value = false
  }
}
const saveItem = async () => {
  if (saving.value || uploading.value) return
  const valid = await itemFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await request.post('/service-items/save', currentItem)
    ElMessage.success('服务项目保存成功，小程序下拉刷新即可查看')
    modalVisible.value = false
    list.load()
  } catch {
  } finally {
    saving.value = false
  }
}

// 上/下架是切换接口：同一行执行中不再响应点击，避免双击切回原状态（R3）
const statusAction = useRowSubmit(async (id, toOffShelf) => {
  try {
    await request.post(`/service-items/${id}/toggle-status`)
    ElMessage.success(toOffShelf ? '已下架，居民端将不再显示该服务' : '已上架，居民下拉刷新即可预约')
    list.load()
  } catch {}
})

async function toggleStatus(row) {
  const toOffShelf = row.status === 'ON_SHELF'
  if (toOffShelf) {
    const ok = await confirmDanger(
      `下架「${row.name}」后，居民将无法再预约这项服务；已下的订单不受影响。`,
      '下架服务',
      '确认下架'
    )
    if (!ok) return
  }
  statusAction.run(row.id, toOffShelf)
}
</script>

<style scoped>
.header-bar {
  margin-bottom: 16px;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.service-thumbnail {
  display: block;
  width: 96px;
  height: 68px;
  object-fit: cover;
  border-radius: 10px;
  background: #edf2e8;
}
.image-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}
.cover-preview {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border-radius: 12px;
  background: #edf2e8;
}
.image-actions p {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.7;
  margin-top: 8px;
}
</style>
