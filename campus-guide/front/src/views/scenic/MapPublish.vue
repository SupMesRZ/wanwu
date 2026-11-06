<template>
  <div class="map-publish-page">
    <aside class="draft-panel">
      <div class="panel-head">
        <h2>地图版本</h2>
        <el-button :icon="Refresh" circle @click="loadDrafts" />
      </div>
      <div class="draft-list">
        <button
          v-for="draft in drafts"
          :key="draft.id"
          :class="['draft-row', { active: selectedDraftId === draft.id }]"
          @click="selectDraft(draft)"
        >
          <span class="draft-main">
            <strong>{{ draft.name }}</strong>
            <em>保存 {{ formatDateTime(draft.lastSavedAt || draft.updatedAt || draft.createdAt) }}</em>
          </span>
          <el-tag :type="draft.status === 'ONLINE' ? 'success' : 'info'" effect="light">
            {{ draft.status }}
          </el-tag>
        </button>
        <div v-if="!drafts.length" class="empty-box">
          <strong>暂无地图草稿</strong>
          <el-button type="primary" @click="goWorkbench">去工作台创建</el-button>
        </div>
      </div>
    </aside>

    <main class="publish-main">
      <section class="publish-hero">
        <div>
          <h1>{{ selectedDraft?.name || '未选择草稿' }}</h1>
        </div>
        <div class="hero-actions">
          <el-button :icon="MapLocation" @click="goWorkbench">地图工作台</el-button>
          <el-button :icon="Refresh" :disabled="!selectedDraft" @click="loadCheck">重新校验</el-button>
          <el-button
            type="success"
            :icon="Upload"
            :disabled="!canPublish"
            :loading="publishing"
            @click="publishDraft"
          >
            发布上线
          </el-button>
        </div>
      </section>

      <section v-if="selectedDraft && checkResult" class="summary-grid">
        <article class="score-card">
          <el-progress
            type="dashboard"
            :percentage="checkResult.score || 0"
            :status="checkResult.publishable ? 'success' : 'exception'"
          />
          <div class="score-copy">
            <strong>{{ checkResult.publishable ? '可发布' : '需处理' }}</strong>
            <span>{{ checkResult.blockerCount }} 个阻塞项 · {{ checkResult.warningCount }} 个提醒项</span>
          </div>
        </article>
        <article v-for="item in metricCards" :key="item.key" class="metric-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section v-if="selectedDraft && checkResult" class="issue-panel">
        <div class="table-panel__header">
          <div>
            <h2>校验结果</h2>
            <span>{{ formatDateTime(checkResult.checkedAt) }}</span>
          </div>
          <el-tag :type="checkResult.publishable ? 'success' : 'danger'" effect="light">
            {{ checkResult.publishable ? '通过' : '未通过' }}
          </el-tag>
        </div>
        <el-table :data="checkResult.issues" border stripe empty-text="没有阻塞或提醒项">
          <el-table-column prop="level" label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="row.level === 'ERROR' ? 'danger' : 'warning'" effect="light">
                {{ row.level === 'ERROR' ? '阻塞' : '提醒' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="问题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="detail" label="处理说明" min-width="360" show-overflow-tooltip />
          <el-table-column prop="code" label="规则编码" width="190" show-overflow-tooltip />
        </el-table>
      </section>

      <section v-if="selectedDraft && checkResult" class="publish-note">
        <el-alert
          v-if="selectedDraft.status === 'ONLINE'"
          title="该草稿已经是当前用户端线上版本"
          type="success"
          :closable="false"
          show-icon
        />
        <el-alert
          v-else-if="!checkResult.publishable"
          title="存在阻塞项时后端会拒绝发布，请先回到地图工作台处理"
          type="error"
          :closable="false"
          show-icon
        />
        <el-alert
          v-else
          title="发布成功后，用户端地图、地点触发和 AI 路线推荐都会读取该 ONLINE 草稿"
          type="info"
          :closable="false"
          show-icon
        />
      </section>

      <section v-if="!selectedDraft" class="empty-main">
        <el-icon><MapLocation /></el-icon>
        <strong>请选择地图草稿</strong>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { MapLocation, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const drafts = ref([])
const selectedDraftId = ref(null)
const checkResult = ref(null)
const loading = ref(false)
const publishing = ref(false)

const selectedDraft = computed(() => drafts.value.find(item => item.id === selectedDraftId.value) || null)
const canPublish = computed(() => selectedDraft.value && selectedDraft.value.status !== 'ONLINE' && checkResult.value?.publishable)

const metricCards = computed(() => {
  const data = checkResult.value || {}
  return [
    { key: 'spots', label: '地点', value: data.spotCount || 0, meta: '启用地点需有围栏' },
    { key: 'facilities', label: '服务点', value: data.facilityCount || 0, meta: '坐标缺失为提醒' },
    { key: 'segments', label: '路段', value: data.routeSegmentCount || 0, meta: 'AI 路线图谱' },
    { key: 'presets', label: '启用预置路线', value: data.enabledPresetRouteCount || 0, meta: `${data.presetRouteCount || 0} 条总路线` }
  ]
})

const loadDrafts = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/map-drafts')
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '加载地图草稿失败')
      return
    }
    drafts.value = res.data || []
    if (!selectedDraftId.value && drafts.value.length) {
      const online = drafts.value.find(item => item.status === 'ONLINE')
      selectedDraftId.value = (online || drafts.value[0]).id
    }
    if (selectedDraftId.value) {
      await loadCheck()
    }
  } finally {
    loading.value = false
  }
}

const selectDraft = async (draft) => {
  selectedDraftId.value = draft.id
  await loadCheck()
}

const loadCheck = async () => {
  if (!selectedDraftId.value) return
  const res = await request.get(`/admin/map-drafts/${selectedDraftId.value}/publish-check`)
  if (res.code !== 'OK') {
    ElMessage.warning(res.message || '发布校验失败')
    return
  }
  checkResult.value = res.data
}

const publishDraft = async () => {
  if (!canPublish.value) return
  try {
    await ElMessageBox.confirm(`确定发布「${selectedDraft.value.name}」为线上地图吗？`, '发布上线', {
      confirmButtonText: '发布上线',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  publishing.value = true
  try {
    const res = await request.post(`/admin/map-drafts/${selectedDraftId.value}/publish`)
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '发布失败')
      return
    }
    ElMessage.success('地图已发布上线')
    await loadDrafts()
  } finally {
    publishing.value = false
  }
}

const goWorkbench = () => {
  router.push('/scenic/map-workbench')
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(loadDrafts)
</script>

<style scoped>
.map-publish-page {
  min-height: calc(100vh - 40px);
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
  background: #f5f7fb;
}

.draft-panel,
.publish-hero,
.score-card,
.metric-card,
.issue-panel,
.publish-note,
.empty-main {
  border: 0;
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.draft-panel {
  min-width: 0;
  padding: 12px;
}

.panel-head,
.publish-hero,
.hero-actions,
.score-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h2,
.issue-panel h2 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
}

.draft-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.draft-row {
  width: 100%;
  min-height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-md);
  background: #ffffff;
  cursor: pointer;
  text-align: left;
}

.draft-row.active {
  border-color: #0f766e;
  background: #f0fdfa;
}

.draft-main {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.draft-main strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-main em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.publish-main {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 16px;
}

.publish-hero {
  padding: 24px;
  background: linear-gradient(135deg, #ffffff, #f0fdfa);
}

.publish-hero h1 {
  margin: 0;
  color: #0f172a;
  font-size: 22px;
}

.summary-grid {
  display: grid;
  grid-template-columns: 280px repeat(4, minmax(140px, 1fr));
  gap: 12px;
}

.score-card,
.metric-card {
  min-height: 112px;
  padding: 18px;
}

.score-copy,
.metric-card {
  display: grid;
  align-content: center;
  gap: 6px;
}

.score-copy strong,
.metric-card strong {
  color: #0f172a;
  font-size: 24px;
}

.score-copy span,
.metric-card span {
  color: #64748b;
  font-size: 12px;
}

.issue-panel {
  overflow: hidden;
}

.publish-note {
  padding: 12px;
}

.empty-box,
.empty-main {
  min-height: 260px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: #64748b;
}

.empty-main .el-icon {
  color: #0f766e;
  font-size: 42px;
}

@media (max-width: 1180px) {
  .map-publish-page,
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .publish-hero,
  .hero-actions {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}
</style>
