import { ref } from 'vue'
import request from './request'

// 权限集不放进 Token，登录和刷新页面时重新拉取，后台改了授权立刻生效
const permissions = ref([])
const isRoot = ref(false)
const loaded = ref(false)

/**
 * 拉取当前管理员的身份与权限码
 */
export const loadCurrentUser = async () => {
  if (!localStorage.getItem('admin_token')) {
    resetCurrentUser()
    return null
  }
  try {
    const res = await request.get('/admin/users/me')
    if (res.code !== 'OK') return null
    permissions.value = res.data.permissions || []
    isRoot.value = Boolean(res.data.isRoot)
    loaded.value = true
    localStorage.setItem('admin_is_root', String(isRoot.value))
    if (res.data.username) {
      localStorage.setItem('admin_username', res.data.username)
    }
    return res.data
  } catch (err) {
    console.error('加载当前管理员权限失败', err)
    return null
  }
}

export const resetCurrentUser = () => {
  permissions.value = []
  isRoot.value = false
  loaded.value = false
}

/**
 * root 一律放行，与后端切面口径保持一致，避免菜单比接口更严
 */
export const hasPermission = (code) => {
  if (!code) return true
  if (isRoot.value) return true
  return permissions.value.includes(code)
}

export const hasAnyPermission = (codes) => {
  if (!codes || codes.length === 0) return true
  return codes.some(hasPermission)
}

export { permissions, isRoot, loaded }
