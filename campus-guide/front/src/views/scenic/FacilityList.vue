<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <el-form class="toolbar-form" :model="filters" @submit.prevent>
        <div class="toolbar-fields">
          <el-form-item label="关键词">
            <el-input
              v-model="filters.keyword"
              :prefix-icon="Search"
              clearable
              placeholder="设施名称 / 类型 / 描述"
              style="width: 260px"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="filters.facilityType" clearable placeholder="全部类型" style="width: 160px">
              <el-option v-for="item in facilityTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 130px">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
        </div>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增设施</el-button>
        </div>
      </el-form>
    </section>

    <section class="table-panel">
      <div class="table-panel__header">
        <div>
          <h2>设施管理</h2>
          <span>维护校园服务中心、卫生间、停车场和食堂等服务点</span>
        </div>
        <el-tag effect="light">{{ currentArea?.name || '默认校区' }}</el-tag>
      </div>
      <el-table
        v-loading="loading"
        :data="pagedRecords"
        stripe
        border
        height="calc(100vh - 236px)"
        empty-text="暂无设施数据"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="设施名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="facilityType" label="类型" width="130">
          <template #default="{ row }">
            <el-tag effect="light">{{ facilityTypeLabel(row.facilityType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="位置/说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="longitude" label="经度" width="130" />
        <el-table-column prop="latitude" label="纬度" width="130" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="210">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button
                size="small"
                :type="row.status === 1 ? 'warning' : 'success'"
                plain
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button size="small" type="danger" plain :icon="Delete" @click="deleteFacility(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredRecords.length"
          background
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑设施' : '新增设施'"
      width="720px"
      destroy-on-close
      append-to-body
    >
      <el-form label-position="top" class="facility-form" @submit.prevent>
        <el-form-item label="设施名称 *">
          <el-input v-model.trim="form.name" clearable maxlength="100" />
        </el-form-item>
        <el-form-item label="设施类型 *">
          <el-select v-model="form.facilityType" filterable placeholder="请选择" style="width: 100%">
            <el-option v-for="item in facilityTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="经度 *">
          <el-input-number v-model="form.longitude" :precision="7" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="纬度 *">
          <el-input-number v-model="form.latitude" :precision="7" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item class="form-wide" label="位置/说明">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitFacility">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const facilityTypes = [
  { label: '校园服务中心', value: 'VISITOR_CENTER' },
  { label: '卫生间', value: 'RESTROOM' },
  { label: '停车场', value: 'PARKING' },
  { label: '餐饮休息', value: 'DINING' },
  { label: '医疗救助', value: 'MEDICAL' },
  { label: '出入口', value: 'ENTRANCE' },
  { label: '其他', value: 'OTHER' }
]

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const currentArea = ref(null)
const records = ref([])
const editingId = ref(null)

const filters = reactive({
  keyword: '',
  facilityType: '',
  status: null
})

const pagination = reactive({
  page: 1,
  pageSize: 20
})

const form = reactive({
  name: '',
  facilityType: 'RESTROOM',
  description: '',
  longitude: null,
  latitude: null,
  status: 1
})

const filteredRecords = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return records.value.filter(item => {
    const matchesKeyword = !keyword || [item.name, item.facilityType, item.description]
      .some(value => String(value || '').toLowerCase().includes(keyword))
    const matchesType = !filters.facilityType || item.facilityType === filters.facilityType
    const matchesStatus = filters.status === null || filters.status === undefined || item.status === filters.status
    return matchesKeyword && matchesType && matchesStatus
  })
})

const pagedRecords = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return filteredRecords.value.slice(start, start + pagination.pageSize)
})

const loadCurrentArea = async () => {
  const res = await request.get('/admin/scenic-areas')
  if (res.code !== 'OK') {
    ElMessage.warning(res.message || '加载校区失败')
    return null
  }
  currentArea.value = Array.isArray(res.data) ? res.data[0] : res.data?.records?.[0]
  return currentArea.value
}

const loadData = async () => {
  loading.value = true
  try {
    const area = currentArea.value || await loadCurrentArea()
    if (!area?.id) {
      records.value = []
      return
    }
    const res = await request.get(`/admin/scenic-areas/${area.id}/facilities`)
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载设施失败')
      return
    }
    records.value = res.data || []
  } finally {
    loading.value = false
  }
}

const search = () => {
  pagination.page = 1
}

const resetFilters = () => {
  filters.keyword = ''
  filters.facilityType = ''
  filters.status = null
  search()
}

const openCreate = () => {
  editingId.value = null
  Object.assign(form, {
    name: '',
    facilityType: 'RESTROOM',
    description: '',
    longitude: null,
    latitude: null,
    status: 1
  })
  dialogVisible.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name || '',
    facilityType: row.facilityType || 'OTHER',
    description: row.description || '',
    longitude: Number(row.longitude),
    latitude: Number(row.latitude),
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

const submitFacility = async () => {
  if (!currentArea.value?.id) {
    ElMessage.warning('缺少当前校区')
    return
  }
  if (!form.name || !form.facilityType || form.longitude === null || form.latitude === null) {
    ElMessage.warning('请填写名称、类型和坐标')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      facilityType: form.facilityType,
      description: form.description,
      longitude: form.longitude,
      latitude: form.latitude,
      status: form.status
    }
    const res = editingId.value
      ? await request.put(`/admin/facilities/${editingId.value}`, payload)
      : await request.post(`/admin/scenic-areas/${currentArea.value.id}/facilities`, payload)
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '保存设施失败')
      return
    }
    ElMessage.success('设施已保存')
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row) => {
  const res = await request.put(`/admin/facilities/${row.id}`, {
    name: row.name,
    facilityType: row.facilityType,
    description: row.description,
    longitude: row.longitude,
    latitude: row.latitude,
    status: row.status === 1 ? 0 : 1
  })
  if (res.code !== 'OK') {
    ElMessage.warning(res.message || '状态更新失败')
    return
  }
  ElMessage.success('状态已更新')
  await loadData()
}

const deleteFacility = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除设施「${row.name}」吗？`, '删除设施', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  const res = await request.delete(`/admin/facilities/${row.id}`)
  if (res.code !== 'OK') {
    ElMessage.warning(res.message || '删除设施失败')
    return
  }
  ElMessage.success('设施已删除')
  await loadData()
}

const facilityTypeLabel = (value) => facilityTypes.find(item => item.value === value)?.label || value || '-'

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(loadData)
</script>

<style scoped>
.facility-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.facility-form .form-wide {
  grid-column: 1 / -1;
}

@media (max-width: 720px) {
  .facility-form {
    grid-template-columns: 1fr;
  }
}
</style>
