<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <el-form class="toolbar-form" :model="filters" @submit.prevent>
        <div class="toolbar-fields">
          <el-form-item label="时间范围">
            <el-segmented v-model="filters.range" :options="rangeOptions" @change="search" />
          </el-form-item>
          <el-form-item label="关键词">
            <el-input
              v-model="filters.keyword"
              :prefix-icon="Search"
              clearable
              :placeholder="config.placeholder"
              style="width: 240px"
              @keyup.enter="search"
            />
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
        <div>
          <h2>{{ config.title }}</h2>
          <span>{{ config.subtitle }}</span>
        </div>
        <el-tag effect="light">真实业务数据</el-tag>
      </div>
      <el-table
        v-loading="loading"
        :data="records"
        stripe
        border
        height="calc(100vh - 236px)"
        empty-text="暂无真实业务数据"
      >
        <el-table-column
          v-for="column in config.columns"
          :key="column.key"
          :prop="column.key"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-tag v-if="column.type === 'tag'" :type="tagType(row[column.key])" effect="light">
              {{ row[column.key] || '-' }}
            </el-tag>
            <span v-else-if="column.type === 'datetime'" class="text-muted">
              {{ formatDateTime(row[column.key]) }}
            </span>
            <span v-else>{{ formatValue(row[column.key]) }}</span>
          </template>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  type: { type: String, required: true }
})

const rangeOptions = [
  { label: '今日', value: 'today' },
  { label: '近7天', value: 'week' },
  { label: '近30天', value: 'month' }
]

const pageConfigs = {
  hotQuestions: {
    title: '热门问题',
    subtitle: '按用户真实提问内容聚合，辅助运营补 FAQ 和知识库',
    endpoint: '/admin/analytics/hot-questions',
    placeholder: '问题内容',
    columns: [
      { key: 'rank', label: '排名', width: 80 },
      { key: 'question', label: '问题', minWidth: 320 },
      { key: 'count', label: '出现次数', width: 110 },
      { key: 'source', label: '来源', width: 120, type: 'tag' },
      { key: 'latestAt', label: '最近出现', minWidth: 160, type: 'datetime' }
    ]
  },
  heatmap: {
    title: '用户热力点',
    subtitle: '按已接受定位点聚合到约 10 米网格，观察真实人流热点',
    endpoint: '/admin/analytics/heatmap',
    placeholder: '经纬度片段',
    columns: [
      { key: 'rank', label: '排名', width: 80 },
      { key: 'area', label: '坐标网格', minWidth: 180 },
      { key: 'visitors', label: '用户数', width: 110 },
      { key: 'crowdLevel', label: '拥挤等级', width: 110, type: 'tag' },
      { key: 'avgAccuracyMeter', label: '平均精度(m)', width: 120 },
      { key: 'latestReportedAt', label: '最近上报', minWidth: 160, type: 'datetime' }
    ]
  },
  routeStats: {
    title: '路线统计',
    subtitle: '按 AI 路线实例聚合选择次数、完成率和预计耗时',
    endpoint: '/admin/analytics/route-stats',
    placeholder: '路线名称',
    columns: [
      { key: 'rank', label: '排名', width: 80 },
      { key: 'name', label: '路线名称', minWidth: 220 },
      { key: 'selectedCount', label: '选择次数', width: 110 },
      { key: 'completedCount', label: '完成数', width: 100 },
      { key: 'completionRate', label: '完成率', width: 100 },
      { key: 'avgDurationMin', label: '平均耗时(分)', width: 130 },
      { key: 'latestCreatedAt', label: '最近生成', minWidth: 160, type: 'datetime' }
    ]
  },
  fenceStats: {
    title: '围栏统计',
    subtitle: '按地点围栏聚合触发、播报和重复进入情况',
    endpoint: '/admin/analytics/fence-stats',
    placeholder: '地点名称 / 围栏 ID',
    columns: [
      { key: 'rank', label: '排名', width: 80 },
      { key: 'name', label: '围栏/地点', minWidth: 200 },
      { key: 'spotId', label: '地点 ID', minWidth: 130 },
      { key: 'triggerCount', label: '触发次数', width: 110 },
      { key: 'broadcastRate', label: '播报率', width: 100 },
      { key: 'repeatRate', label: '重复率', width: 100 },
      { key: 'latestTriggeredAt', label: '最近触发', minWidth: 160, type: 'datetime' }
    ]
  }
}

const config = computed(() => pageConfigs[props.type] || pageConfigs.hotQuestions)
const loading = ref(false)
const records = ref([])

const filters = reactive({
  keyword: '',
  range: 'month'
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get(config.value.endpoint, {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize,
        keyword: filters.keyword || undefined,
        range: filters.range
      }
    })
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载运营数据失败')
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
  filters.range = 'month'
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

const formatValue = (value) => {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return value
}

const tagType = (value) => {
  if (value === '高') return 'danger'
  if (value === '中') return 'warning'
  if (value === '低') return 'success'
  return 'info'
}

watch(
  () => props.type,
  () => {
    pagination.page = 1
    pagination.total = 0
    records.value = []
    loadData()
  }
)

onMounted(loadData)
</script>
