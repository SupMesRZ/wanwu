<template>
  <PageTemplate
    ref="pageRef"
    :columns="columns"
    :data-list="dataList"
    :total="total"
    :form-fields="formFields"
    :modal-title="modalTitle"
    table-title="标签管理"
    table-sub-title="校园地点分类词表，全局共享；地图工作台按标识引用，改名不影响已发布地图"
    add-text="新增标签"
    :action-width="170"
    @search="handleSearch"
    @reset="handleReset"
    @page-change="handlePageChange"
    @add="handleAdd"
    @edit="handleEdit"
    @delete="handleDelete"
    @submit="handleSubmit"
  >
    <template #filters>
      <el-form-item label="关键词">
        <el-input
          v-model="searchForm.keyword"
          placeholder="标识/名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
    </template>

    <template #cell-name="{ row }">
      <el-tag :color="row.color || undefined" :style="tagStyle(row)" effect="dark" size="small">
        {{ row.name }}
      </el-tag>
      <span class="tag-code">{{ row.code }}</span>
    </template>

    <template #cell-status="{ value }">
      <el-tag :type="value === 1 ? 'success' : 'info'">{{ value === 1 ? '启用' : '停用' }}</el-tag>
    </template>

    <template #modal-form="{ form, isEdit }">
      <el-form label-position="top">
        <el-form-item label="标签标识 *">
          <!-- 标识是草稿里的稳定引用，建后不允许改 -->
          <el-input v-model="form.code" :disabled="isEdit" placeholder="英文小写，如 family-friendly" />
        </el-form-item>
        <el-form-item label="标签名称 *">
          <el-input v-model="form.name" placeholder="如 亲子休闲" />
        </el-form-item>
        <el-form-item label="标签颜色">
          <el-color-picker v-model="form.color" />
          <span class="color-hint">{{ form.color || '默认' }}</span>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="这类标签适合什么地点" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
    </template>
  </PageTemplate>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageTemplate from '../../components/PageTemplate.vue'
import request from '../../utils/request'

const pageRef = ref()

const columns = [
  { key: 'id', title: 'ID', width: '70px' },
  { key: 'name', title: '标签', minWidth: 160 },
  { key: 'description', title: '说明', minWidth: 240 },
  { key: 'status', title: '状态', width: '90px' },
  { key: 'createdAt', title: '创建时间', width: '160px', type: 'datetime' }
]

// 只给新增表单兜底初值，弹窗本体走 modal-form 插槽
const formFields = [
  { key: 'status', default: true },
  { key: 'color', default: '#0f766e' }
]

const searchForm = reactive({ keyword: '' })
const dataList = ref([])
const total = ref(0)
const pagination = reactive({ page: 1, pageSize: 20 })

const tagStyle = (row) => row.color
  ? { color: '#fff', borderColor: row.color }
  : {}

const loadData = async () => {
  try {
    const res = await request.get('/admin/scenic/tags', {
      params: { keyword: searchForm.keyword, page: pagination.page, pageSize: pagination.pageSize }
    })
    if (res.code === 'OK') {
      dataList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载标签失败')
    }
  } catch (err) {
    console.error('加载标签失败', err)
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}
const handleReset = () => {
  searchForm.keyword = ''
  pagination.page = 1
  loadData()
}
const handlePageChange = ({ page, pageSize }) => {
  pagination.page = page
  pagination.pageSize = pageSize
  loadData()
}

const modalTitle = ref('')
const editingId = ref(null)

const handleAdd = () => {
  editingId.value = null
  modalTitle.value = '新增标签'
  pageRef.value.openModal(null)
}

const handleEdit = (row) => {
  editingId.value = row.id
  modalTitle.value = `编辑标签 - ${row.name}`
  pageRef.value.openModal({ ...row, status: row.status === 1 })
}

const handleDelete = async (row) => {
  const res = await request.delete(`/admin/scenic/tags/${row.id}`)
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '删除失败')
    return
  }
  ElMessage.success('删除成功，已发布地图中的引用会自动失效')
  loadData()
}

const handleSubmit = async ({ form, isEdit }) => {
  if (!form.code && !isEdit) {
    ElMessage.warning('请填写标签标识')
    return
  }
  if (!form.name) {
    ElMessage.warning('请填写标签名称')
    return
  }
  const payload = {
    code: form.code,
    name: form.name,
    color: form.color,
    description: form.description,
    status: form.status ? 1 : 0
  }
  const url = isEdit ? `/admin/scenic/tags/${editingId.value}` : '/admin/scenic/tags'
  const res = await request[isEdit ? 'put' : 'post'](url, payload)
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '保存失败')
    return
  }
  pageRef.value.closeModal()
  ElMessage.success('保存成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.tag-code {
  margin-left: 8px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.color-hint {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
