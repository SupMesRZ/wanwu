<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <div class="toolbar-title">
        <h2>限流配置</h2>
        <span>规则存于配置中心（ops-rate-limit-rules），命中多条规则时全部都要通过；已认证按身份计数，匿名按 IP</span>
      </div>
      <div class="toolbar-actions">
        <el-switch
          v-model="enabled"
          active-text="限流已启用"
          inactive-text="限流已停用"
          @change="saveEnabled"
        />
        <el-button type="primary" :icon="Plus" @click="addRule">新增规则</el-button>
        <el-button type="primary" plain :loading="saving" @click="saveRules">保存规则</el-button>
      </div>
    </section>

    <section class="table-panel">
      <el-table :data="rules" border stripe empty-text="暂无规则，不限流">
        <el-table-column label="规则名" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="如 auth" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="路径模式" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.pattern" placeholder="/api/v1/system/auth/**" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="方法" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.methods" multiple collapse-tags size="small" placeholder="不限方法" clearable>
              <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="窗口内限次" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.limit" :min="1" :max="100000" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="窗口秒数" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.windowSeconds" :min="1" :max="86400" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ $index }">
            <el-button size="small" type="danger" :icon="Delete" @click="rules.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>

      <div class="footer-hint">
        <el-alert type="info" :closable="false" show-icon>
          规则名是计数 key 的一部分，改名等于重置计数；Redis 故障时限流自动放行，不会阻断业务
        </el-alert>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']

const enabled = ref(true)
const rules = ref([])
const saving = ref(false)

const loadConfig = async () => {
  try {
    const [enabledRes, rulesRes] = await Promise.all([
      request.get('/admin/configs/domains/system/ops-rate-limit-enabled'),
      request.get('/admin/configs/domains/system/ops-rate-limit-rules')
    ])
    if (enabledRes.code === 'OK') enabled.value = enabledRes.data?.configValue !== 'false'
    if (rulesRes.code === 'OK') {
      const raw = rulesRes.data?.configValue || '[]'
      try {
        const parsed = JSON.parse(raw)
        rules.value = Array.isArray(parsed) ? parsed.map(normalizeRule) : []
      } catch {
        ElMessage.error('现有限流规则 JSON 解析失败，请检查配置中心原始值')
        rules.value = []
      }
    }
  } catch (err) {
    console.error('加载限流配置失败', err)
  }
}

const normalizeRule = (item) => ({
  name: item.name || '',
  pattern: item.pattern || '',
  methods: Array.isArray(item.methods) ? item.methods : [],
  limit: item.limit || 60,
  windowSeconds: item.windowSeconds || 60,
  enabled: item.enabled !== false
})

const addRule = () => {
  rules.value.push({
    name: '',
    pattern: '/api/v1/',
    methods: ['POST'],
    limit: 60,
    windowSeconds: 60,
    enabled: true
  })
}

const validate = () => {
  for (const [index, rule] of rules.value.entries()) {
    if (!rule.name?.trim()) return `第 ${index + 1} 条规则缺少规则名`
    if (!/^[a-z0-9-]+$/i.test(rule.name.trim())) return `规则名「${rule.name}」只能含字母数字和中划线`
    if (!rule.pattern?.trim()) return `第 ${index + 1} 条规则缺少路径模式`
    if (!rule.limit || rule.limit < 1) return `规则「${rule.name}」限次必须大于 0`
    if (!rule.windowSeconds || rule.windowSeconds < 1) return `规则「${rule.name}」窗口秒数必须大于 0`
  }
  const names = rules.value.map(r => r.name.trim())
  if (new Set(names).size !== names.length) return '规则名不能重复'
  return null
}

const saveEnabled = async (value) => {
  const res = await request.put('/admin/configs/domains/system/ops-rate-limit-enabled', {
    value: String(value)
  })
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '保存失败')
    enabled.value = !value
    return
  }
  ElMessage.success(value ? '限流已启用' : '限流已停用')
}

const saveRules = async () => {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  saving.value = true
  try {
    const payload = rules.value.map(rule => ({
      name: rule.name.trim(),
      pattern: rule.pattern.trim(),
      methods: rule.methods,
      limit: rule.limit,
      windowSeconds: rule.windowSeconds,
      enabled: rule.enabled
    }))
    const res = await request.put('/admin/configs/domains/system/ops-rate-limit-rules', {
      value: JSON.stringify(payload)
    })
    if (res.code !== 'OK') {
      ElMessage.error(res.message || '保存失败')
      return
    }
    ElMessage.success('规则已保存，即时生效')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.toolbar-title h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

.toolbar-title span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: var(--el-bg-color);
  border-radius: 8px;
  margin-bottom: 16px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.table-panel {
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 16px 20px;
}

.footer-hint {
  margin-top: 12px;
}
</style>
