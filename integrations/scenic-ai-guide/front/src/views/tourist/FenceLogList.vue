<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <el-form class="toolbar-form" :model="filters" @submit.prevent>
        <div class="toolbar-fields">
          <el-form-item label="会话 ID">
            <el-input-number v-model="filters.sessionId" :min="1" :controls="false" clearable placeholder="全部" />
          </el-form-item>
          <el-form-item label="景点">
            <el-input
              v-model="filters.keyword"
              :prefix-icon="Search"
              clearable
              placeholder="景点名称 / ID"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="播报状态">
            <el-select v-model="filters.broadcastStatus" clearable placeholder="全部状态" style="width: 150px">
              <el-option label="未播报" value="NONE" />
              <el-option label="已触发" value="TRIGGERED" />
              <el-option label="已跳过" value="SKIPPED" />
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
        <h2>围栏触发日志</h2>
        <span>共 {{ pagination.total }} 条</span>
      </div>
      <el-table v-loading="loading" :data="records" stripe height="calc(100vh - 236px)">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="sessionId" label="会话 ID" width="110" />
        <el-table-column prop="spotName" label="景点" min-width="150" show-overflow-tooltip />
        <el-table-column prop="spotId" label="景点 ID" min-width="120" show-overflow-tooltip />
        <el-table-column prop="triggerState" label="触发状态" width="110">
          <template #default="{ row }">
            <el-tag :type="triggerType(row.triggerState)" effect="light">{{ triggerLabel(row.triggerState) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发类型" width="110" />
        <el-table-column prop="broadcastStatus" label="播报" width="100">
          <template #default="{ row }">
            <el-tag :type="broadcastType(row.broadcastStatus)" effect="light">{{ broadcastLabel(row.broadcastStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="accuracyMeter" label="精度" width="90">
          <template #default="{ row }">{{ row.accuracyMeter ? `${row.accuracyMeter}m` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="longitude" label="经度" width="120" />
        <el-table-column prop="latitude" label="纬度" width="120" />
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="reportedAt" label="上报时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.reportedAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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
  broadcastStatus: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/tourist/fence-logs', {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize,
        sessionId: filters.sessionId || undefined,
        keyword: filters.keyword || undefined,
        broadcastStatus: filters.broadcastStatus || undefined
      }
    })
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载围栏日志失败')
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
  filters.broadcastStatus = ''
  search()
}

const handleSizeChange = () => {
  pagination.page = 1
  loadData()
}

const triggerLabel = (status) => {
  const map = {
    ENTERING: '进入判定',
    DWELL: '驻留触发',
    INSIDE: '围栏内',
    EXIT: '离开'
  }
  return map[status] || status || '-'
}

const triggerType = (status) => {
  const map = {
    DWELL: 'success',
    ENTERING: 'warning',
    EXIT: 'info'
  }
  return map[status] || 'info'
}

const broadcastLabel = (status) => {
  const map = {
    NONE: '未播报',
    TRIGGERED: '已触发',
    SKIPPED: '已跳过'
  }
  return map[status] || status || '-'
}

const broadcastType = (status) => {
  const map = {
    TRIGGERED: 'success',
    SKIPPED: 'info',
    NONE: 'warning'
  }
  return map[status] || 'info'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>
