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
              placeholder="会话编号 / 游客标识"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px">
              <el-option label="进行中" value="ACTIVE" />
              <el-option label="已结束" value="ENDED" />
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
        <h2>游客导览会话</h2>
        <span>共 {{ pagination.total }} 条</span>
      </div>
      <el-table v-loading="loading" :data="records" stripe height="calc(100vh - 236px)">
        <el-table-column prop="sessionNo" label="会话编号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="visitorSubject" label="游客标识" min-width="160" show-overflow-tooltip />
        <el-table-column prop="scenicAreaId" label="景区" width="90" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="sessionStatusType(row.status)" effect="light">{{ sessionStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentSpotId" label="当前景点" min-width="120" show-overflow-tooltip />
        <el-table-column prop="currentRouteId" label="当前路线" width="110" />
        <el-table-column prop="lastLocationAt" label="最近定位" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.lastLocationAt) }}</template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
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
    const res = await request.get('/admin/tourist/sessions', {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize,
        keyword: filters.keyword || undefined,
        status: filters.status || undefined
      }
    })
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载会话失败')
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
  filters.keyword = ''
  filters.status = ''
  search()
}

const handleSizeChange = () => {
  pagination.page = 1
  loadData()
}

const sessionStatusLabel = (status) => {
  const map = {
    ACTIVE: '进行中',
    ENDED: '已结束'
  }
  return map[status] || status || '-'
}

const sessionStatusType = (status) => (status === 'ACTIVE' ? 'success' : 'info')

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>
