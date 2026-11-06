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
              placeholder="播报文本摘要"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item label="音色">
            <el-input v-model="filters.voiceCode" clearable placeholder="如 siyue" @keyup.enter="search" />
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
        <h2>TTS 缓存</h2>
        <span>共 {{ pagination.total }} 条</span>
      </div>
      <el-table v-loading="loading" :data="records" stripe height="calc(100vh - 236px)">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="textPreview" label="文本摘要" min-width="260" show-overflow-tooltip />
        <el-table-column prop="voiceCode" label="音色" width="110" />
        <el-table-column prop="outputFormat" label="格式" width="90" />
        <el-table-column prop="sampleRate" label="采样率" width="100" />
        <el-table-column prop="audioBase64Size" label="音频大小" width="110">
          <template #default="{ row }">{{ formatSize(row.audioBase64Size) }}</template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中" width="90" />
        <el-table-column prop="expired" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.expired ? 'warning' : 'success'" effect="light">
              {{ row.expired ? '已过期' : '有效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expireAt" label="过期时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" :icon="VideoPlay" @click="playCache(row)">试听</el-button>
              <el-button size="small" type="danger" plain :icon="Delete" @click="deleteCache(row)">删除</el-button>
            </div>
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
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Delete, Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const records = ref([])
const activeAudio = ref(null)

const filters = reactive({
  keyword: '',
  voiceCode: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/digital-human/tts-cache', {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize,
        keyword: filters.keyword || undefined,
        voiceCode: filters.voiceCode || undefined
      }
    })
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载 TTS 缓存失败')
      return
    }
    records.value = res.data?.records || []
    pagination.total = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

const playCache = async (row) => {
  const res = await request.get(`/admin/digital-human/tts-cache/${row.id}/audio`)
  if (res.code !== 'OK' || !res.data?.audioBase64) {
    ElMessage.warning(res.message || '缓存音频不可用')
    return
  }
  if (activeAudio.value) activeAudio.value.pause()
  const audio = new Audio(`data:audio/${res.data.format || row.outputFormat || 'wav'};base64,${res.data.audioBase64}`)
  activeAudio.value = audio
  audio.play().catch(() => ElMessage.warning('浏览器阻止了自动播放，请再次点击试听'))
}

const deleteCache = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除 TTS 缓存「${row.textPreview || row.id}」吗？`, '删除缓存', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  const res = await request.delete(`/admin/digital-human/tts-cache/${row.id}`)
  if (res.code !== 'OK') {
    ElMessage.warning(res.message || '删除失败')
    return
  }
  ElMessage.success('缓存已删除')
  loadData()
}

const search = () => {
  pagination.page = 1
  loadData()
}

const resetFilters = () => {
  filters.keyword = ''
  filters.voiceCode = ''
  search()
}

const handleSizeChange = () => {
  pagination.page = 1
  loadData()
}

const formatSize = (value) => {
  const size = Number(value || 0)
  if (size <= 0) return '-'
  if (size < 1024) return `${size} B`
  return `${(size / 1024).toFixed(1)} KB`
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadData)

onBeforeUnmount(() => {
  if (activeAudio.value) activeAudio.value.pause()
})
</script>
