<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <div class="toolbar-title">
        <h2>路线策略配置</h2>
        <span>影响 Dijkstra 算路与 AI 选线倾向，保存后即时生效；底层是配置中心的 route 域配置项</span>
      </div>
      <div class="toolbar-actions">
        <el-button type="primary" :loading="saving" @click="saveAll">保存全部</el-button>
      </div>
    </section>

    <section class="config-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <strong>算路权重</strong>
            <el-tag :type="weightSumValid ? 'success' : 'danger'" size="small">
              合计 {{ weightSum.toFixed(1) }}
            </el-tag>
          </div>
        </template>
        <p class="card-desc">三个权重决定"最短路径"里距离、耗时、拥挤度各占多少分量，合计应为 1.0</p>
        <div v-for="item in weightItems" :key="item.key" class="weight-row">
          <span class="weight-label">{{ item.label }}</span>
          <el-slider v-model="form[item.key]" :min="0" :max="1" :step="0.1" show-input :show-input-controls="false" />
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header><strong>AI 偏好标签</strong></template>
        <p class="card-desc">
          配置了偏好标签后，AI 规划路线会在满足用户需求的前提下优先途经带这些标签的地点；
          清空则不干预。标签词表在「校园管理 → 标签管理」维护
        </p>
        <el-select
          v-model="form.preferTags"
          multiple
          filterable
          allow-create
          default-first-option
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择或输入标签标识"
          style="width: 100%"
        >
          <el-option v-for="tag in tagOptions" :key="tag.code" :label="tag.name" :value="tag.code">
            <span>{{ tag.name }}</span>
            <span class="option-code">{{ tag.code }}</span>
          </el-option>
        </el-select>
      </el-card>

      <el-card shadow="never">
        <template #header><strong>预置路线目标上限</strong></template>
        <p class="card-desc">
          单条预置路线最多包含的地点数，地图发布校验时超过会给出警告（不阻断发布）
        </p>
        <el-input-number v-model="form.maxTargets" :min="2" :max="50" controls-position="right" />
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const WEIGHT_KEYS = [
  { key: 'distance', label: '距离', configKey: 'route-dijkstra-weight-distance' },
  { key: 'duration', label: '耗时', configKey: 'route-dijkstra-weight-duration' },
  { key: 'crowd', label: '拥挤度', configKey: 'route-dijkstra-weight-crowd' }
]
const weightItems = WEIGHT_KEYS

const form = reactive({
  distance: 0.5,
  duration: 0.3,
  crowd: 0.2,
  preferTags: [],
  maxTargets: 15
})

const tagOptions = ref([])
const saving = ref(false)

const weightSum = computed(() => form.distance + form.duration + form.crowd)
const weightSumValid = computed(() => Math.abs(weightSum.value - 1) < 0.001)

const readConfig = async (key, fallback) => {
  const res = await request.get(`/admin/configs/domains/route/${key}`)
  return res.code === 'OK' && res.data?.configValue != null && res.data.configValue !== ''
    ? res.data.configValue
    : fallback
}

const loadAll = async () => {
  try {
    const [distance, duration, crowd, preferTags, maxTargets] = await Promise.all([
      readConfig('route-dijkstra-weight-distance', '0.5'),
      readConfig('route-dijkstra-weight-duration', '0.3'),
      readConfig('route-dijkstra-weight-crowd', '0.2'),
      readConfig('route-ai-prefer-tags', ''),
      readConfig('route-max-targets', '15')
    ])
    form.distance = Number(distance) || 0
    form.duration = Number(duration) || 0
    form.crowd = Number(crowd) || 0
    form.preferTags = preferTags ? preferTags.split(/[,，、\s]+/).filter(Boolean) : []
    form.maxTargets = Math.max(2, Number(maxTargets) || 15)
  } catch (err) {
    console.error('加载路线策略配置失败', err)
    ElMessage.error('加载配置失败')
  }
}

const loadTags = async () => {
  try {
    const res = await request.get('/admin/scenic/tags/enabled')
    // 没有 scenic:read 权限时拿不到词表，仍允许手工输入 code
    if (res.code === 'OK') tagOptions.value = res.data || []
  } catch (err) {
    console.error('加载标签词表失败', err)
  }
}

const saveAll = async () => {
  if (!weightSumValid.value) {
    ElMessage.warning(`权重合计应为 1.0，当前为 ${weightSum.value.toFixed(1)}`)
    return
  }
  saving.value = true
  try {
    const writes = [
      ...WEIGHT_KEYS.map(({ key, configKey }) =>
        request.put(`/admin/configs/domains/route/${configKey}`, { value: String(form[key]) })),
      request.put('/admin/configs/domains/route/route-ai-prefer-tags', { value: form.preferTags.join(',') }),
      request.put('/admin/configs/domains/route/route-max-targets', { value: String(form.maxTargets) })
    ]
    const results = await Promise.all(writes)
    const failed = results.find(res => res.code !== 'OK')
    if (failed) {
      ElMessage.error(failed.message || '保存失败')
      return
    }
    ElMessage.success('已保存，即时生效')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadAll()
  loadTags()
})
</script>

<style scoped>
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

.toolbar-title h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

.toolbar-title span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 16px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-desc {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.weight-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.weight-row + .weight-row {
  margin-top: 6px;
}

.weight-label {
  flex: 0 0 52px;
  font-size: 13px;
}

.option-code {
  float: right;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
