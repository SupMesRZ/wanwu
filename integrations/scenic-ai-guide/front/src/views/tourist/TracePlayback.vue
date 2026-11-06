<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <el-form class="toolbar-form" :model="filters" @submit.prevent>
        <div class="toolbar-fields">
          <el-form-item label="会话 ID">
            <el-input-number v-model="filters.sessionId" :min="1" :controls="false" clearable placeholder="全部" />
          </el-form-item>
          <el-form-item label="判定">
            <el-select v-model="filters.accepted" clearable placeholder="全部" style="width: 130px">
              <el-option label="参与判定" :value="1" />
              <el-option label="已拒绝" :value="0" />
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
        <h2>游客轨迹</h2>
        <span>共 {{ pagination.total }} 条</span>
      </div>
      <el-table v-loading="loading" :data="records" stripe height="calc(100vh - 236px)">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="sessionId" label="会话 ID" width="110" />
        <el-table-column prop="accepted" label="判定" width="110">
          <template #default="{ row }">
            <el-tag :type="Number(row.accepted) === 1 ? 'success' : 'warning'" effect="light">
              {{ Number(row.accepted) === 1 ? '参与' : '拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="latitude" label="纬度" width="130" />
        <el-table-column prop="longitude" label="经度" width="130" />
        <el-table-column prop="accuracyMeter" label="精度" width="90">
          <template #default="{ row }">{{ row.accuracyMeter ? `${row.accuracyMeter}m` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="speedMeterSecond" label="速度" width="100">
          <template #default="{ row }">{{ row.speedMeterSecond == null ? '-' : `${row.speedMeterSecond}m/s` }}</template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="拒绝原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="reportedAt" label="上报时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.reportedAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="入库时间" min-width="150">
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
  accepted: undefined
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/tourist/location-logs', {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize,
        sessionId: filters.sessionId || undefined,
        accepted: filters.accepted
      }
    })
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载游客轨迹失败')
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
  filters.accepted = undefined
  search()
}

const handleSizeChange = () => {
  pagination.page = 1
  loadData()
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>
