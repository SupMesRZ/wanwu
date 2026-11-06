<template>
  <div v-if="route.meta.publicPage" class="public-wrapper">
    <router-view></router-view>
  </div>

  <div v-else-if="!showAdminShell" class="login-wrapper">
    <router-view @login-success="checkLoginStatus"></router-view>
  </div>

  <el-container v-else class="admin-shell">
    <el-aside class="admin-aside" width="180px">
      <div class="brand-bar">
        <div class="brand-mark">
          <el-icon><Guide /></el-icon>
        </div>
        <div class="brand-copy">
          <strong>景区AI导览</strong>
          <span>Admin Console</span>
        </div>
      </div>

      <el-scrollbar class="aside-scroll">
        <el-menu
          router
          unique-opened
          class="admin-menu"
          :default-active="route.path"
          :default-openeds="defaultOpeneds"
          background-color="transparent"
        >
          <template v-for="item in visibleMenuItems" :key="item.index">
            <el-menu-item v-if="!item.children" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.name }}</template>
            </el-menu-item>
            <el-sub-menu v-else :index="item.index">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.name }}</span>
              </template>
              <el-menu-item
                v-for="sub in item.children"
                :key="sub.path"
                :index="sub.path"
              >
                {{ sub.name }}
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>

      <div class="aside-account">
        <el-avatar :size="28">{{ adminName.charAt(0).toUpperCase() }}</el-avatar>
        <div class="account-meta">
          <strong>{{ adminName }}</strong>
          <span>{{ isRoot ? '超级管理员' : '系统管理员' }}</span>
        </div>
        <el-tooltip content="退出登录" placement="top">
          <el-button :icon="SwitchButton" circle text @click="handleLogout" />
        </el-tooltip>
      </div>
    </el-aside>

    <el-container class="admin-main">
      <el-main class="admin-content" :class="{ 'no-padding': route.path === '/scenic/map-workbench' }">
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DataAnalysis,
  Guide,
  Location,
  MapLocation,
  Microphone,
  Reading,
  Setting,
  SwitchButton,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import {
  hasPermission,
  isRoot as currentIsRoot,
  loaded as permissionLoaded,
  loadCurrentUser,
  resetCurrentUser
} from './utils/permission'

const router = useRouter()
const route = useRoute()

const isLoggedIn = ref(false)
const adminName = ref('admin')
const isRoot = ref(false)

// perm 对应后端 PermissionCode，缺权限的入口直接不渲染，避免点进去只拿到 403
const menuItems = [
  {
    name: '数据概览',
    path: '/dashboard',
    index: 'dashboard',
    icon: DataAnalysis,
    perm: 'analytics:read'
  },
  {
    name: '景区管理',
    index: 'scenic',
    icon: Location,
    children: [
      { name: '地图工作台', path: '/scenic/map-workbench', perm: 'scenic:read' },
      { name: '当前景区', path: '/scenic/areas', perm: 'scenic:read' },
      { name: '标签管理', path: '/scenic/tags', perm: 'scenic:read' },
      { name: '地图发布', path: '/scenic/map-publish', perm: 'scenic:read' }
    ]
  },
  {
    name: '路线管理',
    index: 'routes',
    icon: MapLocation,
    children: [
      { name: '预制路线', path: '/routes/prefab', perm: 'route:read' },
      { name: 'AI推荐路线', path: '/routes/ai', perm: 'route:read' },
      { name: '策略配置', path: '/routes/config', perm: 'route:write' }
    ]
  },
  {
    name: '知识库',
    index: 'qa',
    icon: Reading,
    children: [
      { name: '知识文档', path: '/qa/knowledge-docs', perm: 'qa:read' },
      { name: '分块管理', path: '/qa/knowledge-chunks', perm: 'qa:read' },
      { name: 'FAQ管理', path: '/qa/faqs', perm: 'qa:read' },
      { name: '讲解词管理', path: '/qa/guide-scripts', perm: 'qa:read' }
    ]
  },
  {
    name: '数字人',
    index: 'digitalhuman',
    icon: Microphone,
    children: [
      { name: '形象与人设', path: '/digitalhuman/avatars', perm: 'dh:read' },
      { name: '声音配置', path: '/digitalhuman/voices', perm: 'dh:read' },
      { name: 'TTS缓存', path: '/digitalhuman/tts-cache', perm: 'dh:read' }
    ]
  },
  {
    name: '游客管理',
    index: 'tourist',
    icon: User,
    children: [
      { name: '会话列表', path: '/tourist/sessions', perm: 'tourist:read' },
      { name: '轨迹回放', path: '/tourist/traces', perm: 'tourist:read' },
      { name: '围栏日志', path: '/tourist/fence-logs', perm: 'tourist:read' }
    ]
  },
  {
    name: '运营分析',
    path: '/analytics/dashboard',
    index: 'analytics',
    icon: TrendCharts,
    perm: 'analytics:read'
  },
  {
    name: '系统设置',
    index: 'system',
    icon: Setting,
    children: [
      { name: '管理员账号', path: '/system/admins', perm: 'system:user:manage' },
      { name: '角色权限', path: '/system/roles', perm: 'system:role:manage' },
      { name: '操作日志', path: '/system/logs', perm: 'system:log:read' },
      { name: '限流配置', path: '/system/rate-limits', perm: 'system:config:read' },
      { name: '全局配置', path: '/system/configs', perm: 'system:config:read' }
    ]
  }
]

// 子项被过滤空的分组整个隐藏，不留空壳菜单
const visibleMenuItems = computed(() => menuItems
  .map(item => (item.children
    ? { ...item, children: item.children.filter(sub => hasPermission(sub.perm)) }
    : item))
  .filter(item => (item.children ? item.children.length > 0 : hasPermission(item.perm))))

const defaultOpeneds = computed(() => {
  const active = visibleMenuItems.value.find(item => item.children?.some(sub => sub.path === route.path))
  return active ? [active.index] : []
})

const showAdminShell = computed(() => isLoggedIn.value && route.path !== '/login' && !route.meta.publicPage)

const checkLoginStatus = async () => {
  const token = localStorage.getItem('admin_token')
  isLoggedIn.value = Boolean(token)
  if (token) {
    // 权限集每次进后台重新拉，后台改了授权无需重新登录就能生效
    if (!permissionLoaded.value) await loadCurrentUser()
    isRoot.value = currentIsRoot.value
    const storedName = localStorage.getItem('admin_username')
    adminName.value = storedName && !['undefined', 'null'].includes(storedName) ? storedName : 'admin'
    if (route.path === '/login') {
      router.push(landingPath())
      return
    }
    // 无权访问默认首页时落到第一个有权限的菜单，避免开局就是一屏 403
    if (route.path === '/dashboard' && !hasPermission('analytics:read')) {
      router.push(landingPath())
    }
    return
  }
  resetCurrentUser()
  if (route.meta.publicPage) return
  if (route.path !== '/login') router.push('/login')
}

const landingPath = () => {
  const first = visibleMenuItems.value[0]
  if (!first) return '/dashboard'
  return first.children ? first.children[0].path : first.path
}

const handleLogout = () => {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_username')
  localStorage.removeItem('admin_is_root')
  resetCurrentUser()
  checkLoginStatus()
}

watch(() => route.path, () => {
  checkLoginStatus()
})

onMounted(() => {
  router.isReady().then(checkLoginStatus)
  window.addEventListener('admin-auth-expired', checkLoginStatus)
})

onBeforeUnmount(() => {
  window.removeEventListener('admin-auth-expired', checkLoginStatus)
})
</script>
