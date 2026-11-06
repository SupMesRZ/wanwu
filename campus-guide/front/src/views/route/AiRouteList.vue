<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <el-form class="toolbar-form" :model="filters" @submit.prevent>
        <div class="toolbar-fields">
          <el-form-item label="会话 ID">
            <el-input-number v-model="filters.sessionId" :min="1" :controls="false" clearable placeholder="全部" />
          </el-form-item>
          <el-form-item label="关键词">
            <el-input
              v-model="filters.keyword"
              :prefix-icon="Search"
              clearable
              placeholder="路线名称"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
              <el-option label="可用" value="ACTIVE" />
              <el-option label="已归档" value="ARCHIVED" />
            </el-select>
          </el-form-item>
        </div>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
      </el-form>
    </section>

    <section class="table-panel">
      <div class="table-panel__header">
        <h2>AI 推荐路线</h2>
        <span>共 {{ pagination.total }} 条</span>
      </div>
      <el-table v-loading="loading" :data="records" stripe height="calc(100vh - 236px)">
        <el-table-column prop="name" label="路线名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="sessionId" label="会话 ID" width="110" />
        <el-table-column prop="scenicAreaId" label="校区" width="90" />
        <el-table-column prop="sourceDraftId" label="地图版本" width="110" />
        <el-table-column prop="routeType" label="类型" width="120">
          <template #default="{ row }">{{ routeTypeLabel(row.routeType) }}</template>
        </el-table-column>
        <el-table-column prop="estimatedDurationMin" label="预计耗时" width="110">
          <template #default="{ row }">{{ row.estimatedDurationMin ? `${row.estimatedDurationMin} 分钟` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">
              {{ routeStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recommendationReason" label="推荐理由" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const records = ref([])

const filters = reactive({
  sessionId: undefined,
  keyword: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/routes/ai', {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize,
        sessionId: filters.sessionId || undefined,
        keyword: filters.keyword || undefined,
        status: filters.status || undefined
      }
    })
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载 AI 路线失败')
      return
    }
    records.value = res.data?.records || []
    pagination.total = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

const search = () => {
  pagination.page = 1
  loadData()
}

const resetFilters = () => {
  filters.sessionId = undefined
  filters.keyword = ''
  filters.status = ''
  search()
}

const handleSizeChange = () => {
  pagination.page = 1
  loadData()
}

const routeTypeLabel = (type) => {
  const map = {
    AI_COMPOSED: 'AI 组合',
    PRESET_BASED: '模板派生'
  }
  return map[type] || type || '-'
}

const routeStatusLabel = (status) => {
  const map = {
    ACTIVE: '可用',
    ARCHIVED: '已归档'
  }
  return map[status] || status || '-'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>
