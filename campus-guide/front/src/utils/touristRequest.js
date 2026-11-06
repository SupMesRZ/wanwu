import axios from 'axios'

const ACCESS_TOKEN_KEY = 'tourist_access_token'
const REFRESH_TOKEN_KEY = 'tourist_refresh_token'
const DEVICE_ID_KEY = 'tourist_device_id'
const AUTH_LEVEL_KEY = 'tourist_auth_level'

const touristRequest = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

touristRequest.interceptors.request.use(config => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

touristRequest.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

const initializeAnonymousIdentity = async () => {
  const existingToken = localStorage.getItem(ACCESS_TOKEN_KEY)
  const existingAuthLevel = localStorage.getItem(AUTH_LEVEL_KEY)
  if (existingToken && ['AUTHENTICATED', 'FENCE'].includes(existingAuthLevel)) {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (refreshToken) {
      try {
        const response = await fetch('/api/v1/system/auth/refresh', {
          method: 'POST',
          headers: { Authorization: `Bearer ${refreshToken}` }
        })
        const result = await response.json()
        if (response.ok && result.code === 'OK') {
          saveTouristTokens(result.data.accessToken, result.data.refreshToken, result.data.authLevel)
          return readTouristIdentity()
        }
      } catch {
        // 刷新失败时降级为匿名身份
      }
    }
  }
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)

  let deviceId = localStorage.getItem(DEVICE_ID_KEY)
  if (!deviceId) {
    const first = await touristRequest.get('/tourist/session/init')
    deviceId = first.data.deviceId
    localStorage.setItem(DEVICE_ID_KEY, deviceId)
  }

  const result = await touristRequest.get('/tourist/session/init', {
    headers: { 'X-Device-Id': deviceId }
  })
  saveTouristTokens(result.data.accessToken, null, result.data.authLevel)
  return readTouristIdentity()
}

const saveTouristTokens = (accessToken, refreshToken, authLevel) => {
  if (accessToken) localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  else localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.setItem(AUTH_LEVEL_KEY, authLevel || 'ANONYMOUS')
  window.dispatchEvent(new Event('tourist-auth-changed'))
}

const clearTouristTokens = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(AUTH_LEVEL_KEY)
  window.dispatchEvent(new Event('tourist-auth-changed'))
}

const readTouristIdentity = () => ({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
  refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY),
  deviceId: localStorage.getItem(DEVICE_ID_KEY),
  authLevel: localStorage.getItem(AUTH_LEVEL_KEY) || 'ANONYMOUS'
})

const streamGuideMessage = async (sessionId, payload, onEvent) => {
  const response = await fetch(`/api/v1/tourist/guide/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream, application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem(ACCESS_TOKEN_KEY) || ''}`
    },
    body: JSON.stringify(payload)
  })
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const result = await response.json().catch(() => null)
    if (!response.ok || result?.code !== 'OK') {
      throw new Error(result?.message || `请求失败（${response.status}）`)
    }
    return
  }
  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    throw new Error(errorBody?.message || `请求失败（${response.status}）`)
  }
  if (!response.body) throw new Error('当前浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      const data = block.split(/\r?\n/)
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).trimStart())
        .join('\n')
      if (data) onEvent(JSON.parse(data))
    }
    if (done) break
  }
}

export {
  clearTouristTokens,
  initializeAnonymousIdentity,
  readTouristIdentity,
  saveTouristTokens,
  streamGuideMessage
}

export default touristRequest
