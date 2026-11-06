<template>
  <main class="route-overview">
    <header class="overview-head">
      <div>
        <span class="kicker">路线档案</span>
        <h1>{{ currentDraft?.name || '预置路线' }}</h1>
      </div>
      <div class="head-actions">
        <el-tag v-if="currentDraft" :type="isOnline ? 'success' : 'info'" effect="light">{{ currentDraft.status }}</el-tag>
        <el-button type="primary" :icon="MapLocation" @click="openWorkbench()">地图工作台</el-button>
      </div>
    </header>

    <section v-if="currentDraft" class="overview-content">
      <div class="metrics">
        <div><span>预置路线</span><strong>{{ routes.length }}</strong></div>
        <div><span>已启用</span><strong>{{ enabledCount }}</strong></div>
        <div><span>景点覆盖</span><strong>{{ coveredSpotCount }}</strong></div>
      </div>

      <section class="route-library">
        <div class="library-head">
          <h2>路线列表</h2>
          <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索路线" />
        </div>
        <div v-if="filteredRoutes.length" class="route-grid">
          <article v-for="route in filteredRoutes" :key="route.id" class="route-card">
            <div class="card-top">
              <div><h3>{{ route.name || '未命名路线' }}</h3><p>{{ route.audience || '大众游客' }} · {{ route.durationMin || 0 }} 分钟</p></div>
              <el-tag :type="Number(route.status ?? 1) === 1 ? 'success' : 'info'" effect="light">{{ Number(route.status ?? 1) === 1 ? '启用' : '停用' }}</el-tag>
            </div>
            <div class="route-chain">
              <template v-for="(spotId, index) in route.spotIds.slice(0, 4)" :key="`${spotId}-${index}`">
                <span v-if="index" class="chain-arrow">→</span><span class="chain-node">{{ getSpotName(spotId) }}</span>
              </template>
              <span v-if="route.spotIds.length > 4" class="chain-more">+{{ route.spotIds.length - 4 }}</span>
            </div>
            <button class="edit-link" type="button" @click="openWorkbench(route.id)">在地图中查看与编辑 <span>→</span></button>
          </article>
        </div>
        <div v-else class="empty-state">暂无预置路线，前往地图工作台创建</div>
      </section>
    </section>

    <section v-else class="empty-state page-empty">暂无地图草稿，前往地图工作台创建</section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MapLocation, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const drafts = ref([])
const currentDraft = ref(null)
const routes = ref([])
const spots = ref([])
const keyword = ref('')
const isOnline = computed(() => currentDraft.value?.status === 'ONLINE')
const enabledCount = computed(() => routes.value.filter(route => Number(route.status ?? 1) === 1).length)
const coveredSpotCount = computed(() => new Set(routes.value.flatMap(route => route.spotIds || [])).size)
const filteredRoutes = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return search ? routes.value.filter(route => route.name?.toLowerCase().includes(search)) : routes.value
})

const loadOverview = async () => {
  const listRes = await request.get('/admin/map-drafts')
  if (listRes.code !== 'OK') {
    ElMessage.warning(listRes.message || '加载地图草稿失败')
    return
  }
  drafts.value = listRes.data || []
  const draft = drafts.value.find(item => item.status === 'ONLINE') || drafts.value[0]
  if (!draft) return
  const workbenchRes = await request.get(`/admin/map-drafts/${draft.id}/workbench`)
  if (workbenchRes.code !== 'OK') {
    ElMessage.warning(workbenchRes.message || '加载预置路线失败')
    return
  }
  currentDraft.value = workbenchRes.data.currentDraft || draft
  const payload = workbenchRes.data.payload || {}
  routes.value = payload.presetRoutes || []
  spots.value = payload.spots || []
}

const getSpotName = (id) => spots.value.find(spot => String(spot.id) === String(id))?.name || '未知景点'
const openWorkbench = (presetRouteId) => router.push({ path: '/scenic/map-workbench', query: { draftId: currentDraft.value?.id, ...(presetRouteId ? { presetRouteId } : {}) } })
onMounted(loadOverview)
</script>

<style scoped>
.route-overview { min-height: calc(100vh - 40px); padding: 32px; background: #f6f8fb; color: #0f172a; }
.overview-head { display:flex; justify-content:space-between; align-items:center; gap:24px; padding: 8px 0 28px; }
.kicker { color:#0f766e; font-size:12px; font-weight:800; letter-spacing:.08em; }
h1,h2,h3,p { margin:0; } h1 { margin-top:6px; font-size:28px; } h2 { font-size:16px; } h3 { font-size:16px; }
.head-actions { display:flex; align-items:center; gap:10px; }.overview-content { display:grid; gap:24px; }
.metrics { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:1px; background:#e5e7eb; border-radius:12px; overflow:hidden; }
.metrics div { display:grid; gap:8px; padding:20px 24px; background:#fff; }.metrics span,.card-top p { color:#64748b; font-size:13px; }.metrics strong { font-size:28px; }
.route-library { padding:24px; background:#fff; border-radius:12px; }.library-head { display:flex; justify-content:space-between; align-items:center; gap:20px; margin-bottom:20px; }.library-head .el-input { max-width:260px; }
.route-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(300px, 1fr)); gap:14px; }.route-card { display:grid; gap:22px; padding:20px; background:#fbfcfd; border-radius:10px; }.card-top { display:flex; justify-content:space-between; gap:12px; }.card-top div { display:grid; gap:7px; }
.route-chain { display:flex; align-items:center; flex-wrap:wrap; gap:6px; min-height:28px; }.chain-node { padding:5px 8px; border-radius:6px; background:#e6fffb; color:#0f766e; font-size:12px; font-weight:700; }.chain-arrow,.chain-more { color:#94a3b8; font-size:12px; }
.edit-link { display:flex; justify-content:space-between; padding:0; border:0; background:transparent; color:#2563eb; font-size:13px; font-weight:700; cursor:pointer; }.edit-link:hover { color:#0f766e; }.empty-state { padding:64px 24px; text-align:center; color:#64748b; }.page-empty { background:#fff; border-radius:12px; }
@media (max-width:720px) { .route-overview { padding:20px; }.overview-head,.library-head { align-items:flex-start; flex-direction:column; }.metrics { grid-template-columns:1fr; }.library-head .el-input { max-width:none; width:100%; } }
</style>
