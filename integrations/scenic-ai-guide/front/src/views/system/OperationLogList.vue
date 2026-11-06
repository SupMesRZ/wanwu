<template>
  <PageTemplate
    :columns="columns"
    :data-list="dataList"
    :total="total"
    :show-add="false"
    :show-edit="false"
    :show-delete="false"
    table-title="操作日志"
    table-sub-title="后台所有写操作的审计留痕，只读不可编辑；敏感字段在落库前已脱敏"
    :action-width="90"
    @search="handleSearch"
    @reset="handleReset"
    @page-change="handlePageChange"
  >
    <template #filters>
      <el-form-item label="操作人">
        <el-input
          v-model="searchForm.keyword"
          placeholder="账号名"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="模块">
        <el-select
          v-model="searchForm.module"
          placeholder="全部"
          clearable
          style="width: 150px"
        >
          <el-option v-for="item in modules" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 110px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILURE" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="searchForm.range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 380px"
        />
      </el-form-item>
    </template>

    <template #extra-actions>
      <el-button v-if="isRoot" type="danger" plain :icon="Delete" @click="handlePurge">
        清理过期日志
      </el-button>
    </template>

    <template #table-extra>
      <span class="retention-hint">当前保留 {{ retentionText }}</span>
    </template>

    <template #cell-status="{ value }">
      <el-tag :type="value === 'SUCCESS' ? 'success' : 'danger'">
        {{ value === 'SUCCESS' ? '成功' : '失败' }}
      </el-tag>
    </template>

    <template #cell-durationMs="{ value }">
      <span>{{ value == null ? '-' : `${value} ms` }}</span>
    </template>

    <template #actions="{ row }">
      <el-button size="small" :icon="View" @click="openDetail(row)">详情</el-button>
    </template>
  </PageTemplate>

  <el-drawer v-model="detailVisible" title="操作详情" size="520px">
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="操作人">
        {{ detail.username || '-' }}
        <span v-if="detail.userId" class="text-muted">(ID {{ detail.userId }})</span>
      </el-descriptions-item>
      <el-descriptions-item label="模块">{{ detail.module || '-' }}</el-descriptions-item>
      <el-descriptions-item label="动作">{{ detail.action || '-' }}</el-descriptions-item>
      <el-descriptions-item label="请求">
        {{ detail.httpMethod }} {{ detail.uri }}
      </el-descriptions-item>
      <el-descriptions-item label="来源 IP">{{ detail.ip || '-' }}</el-descriptions-item>
      <el-descriptions-item label="结果">
        <el-tag :type="detail.status === 'SUCCESS' ? 'success' : 'danger'">
          {{ detail.status === 'SUCCESS' ? '成功' : '失败' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item v-if="detail.errorMsg" label="失败原因">
        {{ detail.errorMsg }}
      </el-descriptions-item>
      <el-descriptions-item label="耗时">
        {{ detail.durationMs == null ? '-' : `${detail.durationMs} ms` }}
      </el-descriptions-item>
      <el-descriptions-item label="时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
      <el-descriptions-item label="请求参数">
        <pre class="params-block">{{ prettyParams }}</pre>
      </el-descriptions-item>
    </el-descriptions>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Delete, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTemplate from '../../components/PageTemplate.vue'
import request from '../../utils/request'
import { isRoot as currentIsRoot } from '../../utils/permission'

const isRoot = computed(() => currentIsRoot.value)

const columns = [
  { key: 'createdAt', title: '时间', width: '170px', type: 'datetime' },
  { key: 'username', title: '操作人', width: '120px' },
  { key: 'module', title: '模块', width: '120px' },
  { key: 'action', title: '动作', minWidth: 140 },
  { key: 'uri', title: '路径', minWidth: 220 },
  { key: 'ip', title: 'IP', width: '130px' },
  { key: 'status', title: '结果', width: '90px' },
  { key: 'durationMs', title: '耗时', width: '90px' }
]

const searchForm = reactive({ keyword: '', module: '', status: '', range: null })
const dataList = ref([])
const total = ref(0)
const pagination = reactive({ page: 1, pageSize: 20 })
const modules = ref([])
const retentionDays = ref(null)

const retentionText = computed(() => {
  if (retentionDays.value === null) return '-'
  // 后端把 0/负数视为永久保留，这里保持同一口径
  return retentionDays.value > 0 ? `${retentionDays.value} 天` : '永久'
})

const loadData = async () => {
  try {
    const res = await request.get('/admin/operation-logs', {
      params: {
        keyword: searchForm.keyword || undefined,
        module: searchForm.module || undefined,
        status: searchForm.status || undefined,
        startTime: searchForm.range?.[0] || undefined,
        endTime: searchForm.range?.[1] || undefined,
        page: pagination.page,
        pageSize: pagination.pageSize
      }
    })
    if (res.code === 'OK') {
      dataList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载操作日志失败')
    }
  } catch (err) {
    console.error('加载操作日志失败', err)
  }
}

const loadMeta = async () => {
  try {
    const [moduleRes, retentionRes] = await Promise.all([
      request.get('/admin/operation-logs/modules'),
      request.get('/admin/operation-logs/retention')
    ])
    if (moduleRes.code === 'OK') modules.value = moduleRes.data || []
    if (retentionRes.code === 'OK') retentionDays.value = retentionRes.data
  } catch (err) {
    console.error('加载操作日志元信息失败', err)
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.module = ''
  searchForm.status = ''
  searchForm.range = null
  pagination.page = 1
  loadData()
}

const handlePageChange = ({ page, pageSize }) => {
  pagination.page = page
  pagination.pageSize = pageSize
  loadData()
}

const handlePurge = async () => {
  await ElMessageBox.confirm(
    `将物理删除 ${retentionText.value} 之前的全部日志，删除后不可恢复`,
    '清理确认',
    { type: 'warning', confirmButtonText: '清理', cancelButtonText: '取消' }
  )
  const res = await request.delete('/admin/operation-logs/expired')
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '清理失败')
    return
  }
  ElMessage.success(`已清理 ${res.data} 条`)
  loadData()
}

const detailVisible = ref(false)
const detail = ref(null)

const openDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}

const prettyParams = computed(() => {
  const raw = detail.value?.params
  if (!raw) return '无'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    // 超长参数被后端截断后不再是合法 JSON，原样展示
    return raw
  }
})

onMounted(() => {
  loadData()
  loadMeta()
})
</script>

<style scoped>
.retention-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.params-block {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.6;
}
</style>
