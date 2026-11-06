import { createRouter, createWebHashHistory } from 'vue-router'

// 尚未建设的功能统一走占位页，避免展示演示数据造成"功能已完成"的错觉
const pendingPage = (title) => ({
  component: () => import('../views/Placeholder.vue'),
  meta: { requiresAuth: true, title }
})

const routes = [
  { path: '/', redirect: '/guide' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresGuest: true }
  },
  {
    path: '/guide',
    name: 'TouristGuide',
    component: () => import('../views/tourist/TouristGuide.vue'),
    meta: { publicPage: true }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/scenic/map-workbench',
    name: 'MapWorkbench',
    component: () => import('../views/scenic/MapWorkbench.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/scenic/areas',
    name: 'AreaList',
    component: () => import('../views/scenic/AreaList.vue'),
    meta: { requiresAuth: true, title: '当前校区' }
  },
  {
    path: '/scenic/facilities',
    name: 'FacilityList',
    component: () => import('../views/scenic/FacilityList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/scenic/tags',
    name: 'TagList',
    component: () => import('../views/scenic/TagList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/scenic/map-publish',
    name: 'MapPublish',
    component: () => import('../views/scenic/MapPublish.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/routes/prefab',
    name: 'PrefabRouteList',
    component: () => import('../views/route/PrefabRouteList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/routes/ai',
    name: 'AiRouteList',
    component: () => import('../views/route/AiRouteList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/routes/config',
    name: 'RouteConfig',
    component: () => import('../views/route/RouteConfig.vue'),
    meta: { requiresAuth: true, title: '策略配置' }
  },
  {
    path: '/qa/knowledge-docs',
    name: 'KnowledgeDocList',
    component: () => import('../views/qa/KnowledgeDocList.vue'),
    meta: { requiresAuth: true, title: '知识库' }
  },
  {
    path: '/qa/knowledge-chunks',
    name: 'KnowledgeChunkList',
    component: () => import('../views/qa/KnowledgeChunkList.vue'),
    meta: { requiresAuth: true, title: '知识库' }
  },
  {
    path: '/qa/faqs',
    name: 'FaqList',
    component: () => import('../views/qa/FaqList.vue'),
    meta: { requiresAuth: true, title: '知识库' }
  },
  {
    path: '/qa/guide-scripts',
    name: 'GuideScriptList',
    component: () => import('../views/qa/GuideScriptList.vue'),
    meta: { requiresAuth: true, title: '知识库' }
  },
  {
    path: '/digitalhuman/avatars',
    name: 'AvatarList',
    component: () => import('../views/DigitalHuman.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/digitalhuman/voices',
    name: 'VoiceList',
    component: () => import('../views/DigitalHumanVoice.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/digitalhuman/personas',
    name: 'PersonaList',
    redirect: '/digitalhuman/avatars',
    meta: { requiresAuth: true }
  },
  {
    path: '/digitalhuman/tts-cache',
    name: 'TtsCacheList',
    component: () => import('../views/DigitalHumanTtsCache.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/tourist/sessions',
    name: 'SessionList',
    component: () => import('../views/tourist/SessionList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/tourist/traces',
    name: 'TracePlayback',
    component: () => import('../views/tourist/TracePlayback.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/tourist/fence-logs',
    name: 'FenceLogList',
    component: () => import('../views/tourist/FenceLogList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/analytics/dashboard',
    name: 'AnalyticsDashboard',
    component: () => import('../views/analytics/AnalyticsDashboard.vue'),
    meta: { requiresAuth: true, title: '运营分析' }
  },
  {
    path: '/analytics/hot-questions',
    name: 'HotQuestions',
    redirect: '/analytics/dashboard'
  },
  { path: '/analytics/sentiment', name: 'SentimentAnalysis', redirect: '/analytics/dashboard' },
  { path: '/analytics/word-cloud', name: 'WordCloud', redirect: '/analytics/dashboard' },
  {
    path: '/analytics/heatmap',
    name: 'Heatmap',
    redirect: '/analytics/dashboard'
  },
  {
    path: '/analytics/route-stats',
    name: 'RouteStats',
    redirect: '/analytics/dashboard'
  },
  {
    path: '/analytics/fence-stats',
    name: 'FenceStats',
    redirect: '/analytics/dashboard'
  },
  {
    path: '/system/admins',
    name: 'AdminList',
    component: () => import('../views/system/AdminList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/system/roles',
    name: 'RoleList',
    component: () => import('../views/system/RoleList.vue'),
    meta: { requiresAuth: true }
  },
  // 权限码由后端枚举定义，不存在独立的权限维护页，统一并入角色页勾选
  { path: '/system/permissions', name: 'PermissionList', redirect: '/system/roles' },
  {
    path: '/system/logs',
    name: 'OperationLogList',
    component: () => import('../views/system/OperationLogList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/system/rate-limits',
    name: 'RateLimitConfig',
    component: () => import('../views/system/RateLimitConfig.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/system/configs',
    name: 'GlobalConfig',
    component: () => import('../views/system/GlobalConfig.vue'),
    meta: { requiresAuth: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.matched.some(record => record.meta.publicPage)) {
    next()
    return
  }
  const token = localStorage.getItem('admin_token')
  if (to.matched.some(record => record.meta.requiresAuth)) {
    next(token ? undefined : { name: 'Login' })
    return
  }
  if (to.matched.some(record => record.meta.requiresGuest)) {
    next(token ? { name: 'Dashboard' } : undefined)
    return
  }
  next()
})

export default router
