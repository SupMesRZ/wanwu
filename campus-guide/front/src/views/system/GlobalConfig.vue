<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">全局配置</h2>
        <p class="page-desc">修改后自动刷新缓存并实时生效，无需重启服务</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadItems">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeDomain" @tab-change="loadItems">
        <el-tab-pane
          v-for="domain in domains"
          :key="domain.domain"
          :label="domain.name || domain.domain"
          :name="domain.domain"
        />
      </el-tabs>

      <el-table v-loading="loading" :data="items" border stripe>
        <el-table-column prop="configKey" label="配置键" min-width="240" show-overflow-tooltip />
        <el-table-column prop="configValue" label="当前值" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.configValue" class="config-value">{{ row.configValue }}</span>
            <span v-else class="text-muted">（空）</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该配置域暂无配置项" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="编辑配置" width="520px">
      <el-form label-width="80px">
        <el-form-item label="配置键">
          <el-input :model-value="editing.configKey" disabled />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="editing.configValue" type="textarea" :rows="3" placeholder="请输入配置值" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editing.description" placeholder="配置用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Edit, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const domains = ref([])
const items = ref([])
const activeDomain = ref('')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editing = reactive({ configKey: '', configValue: '', description: '' })

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-')

const loadDomains = async () => {
  const res = await request.get('/admin/configs/domains')
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '配置域加载失败')
    return
  }
  domains.value = res.data || []
  if (!activeDomain.value && domains.value.length) {
    activeDomain.value = domains.value[0].domain
  }
}

const loadItems = async () => {
  if (!activeDomain.value) return
  loading.value = true
  try {
    const res = await request.get(`/admin/configs/domains/${activeDomain.value}`)
    if (res.code === 'OK') {
      items.value = res.data || []
    } else {
      ElMessage.error(res.message || '配置加载失败')
    }
  } finally {
    loading.value = false
  }
}

const openEdit = (row) => {
  editing.configKey = row.configKey
  editing.configValue = row.configValue ?? ''
  editing.description = row.description ?? ''
  dialogVisible.value = true
}

const handleSave = async () => {
  saving.value = true
  try {
    const res = await request.put(
      `/admin/configs/domains/${activeDomain.value}/${editing.configKey}`,
      { value: editing.configValue, description: editing.description }
    )
    if (res.code === 'OK') {
      ElMessage.success('保存成功，已实时生效')
      dialogVisible.value = false
      await loadItems()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadDomains()
  await loadItems()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
}

.page-desc {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.config-value {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>
