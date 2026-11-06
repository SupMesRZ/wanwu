import { computed, reactive } from 'vue'
import touristRequest, {
  clearTouristTokens,
  initializeAnonymousIdentity,
  readTouristIdentity,
  saveTouristTokens
} from '@/utils/touristRequest'

const identity = reactive({
  ready: false,
  authLevel: 'ANONYMOUS',
  presence: 'UNKNOWN'
})

const syncIdentity = () => {
  identity.authLevel = readTouristIdentity().authLevel
}

window.addEventListener('tourist-auth-changed', syncIdentity)

export const useTouristIdentity = () => {
  const initialize = async () => {
    await initializeAnonymousIdentity()
    syncIdentity()
    identity.ready = true
  }

  const login = async (phone, smsCode) => {
    const response = await touristRequest.post('/system/auth/sms/login', { phone, smsCode })
    saveTouristTokens(
      response.data.accessToken,
      response.data.refreshToken,
      response.data.authLevel
    )
    syncIdentity()
    identity.presence = 'UNKNOWN'
  }

  const logout = async () => {
    try {
      await touristRequest.post('/system/auth/logout')
    } finally {
      clearTouristTokens()
      await initializeAnonymousIdentity()
      syncIdentity()
      identity.presence = 'UNKNOWN'
    }
  }

  const sendSmsCode = phone => touristRequest.post('/system/auth/sms/send', { phone })
  const setPresence = value => { identity.presence = value }

  return {
    identity,
    isAuthenticated: computed(() => ['AUTHENTICATED', 'FENCE'].includes(identity.authLevel)),
    isInScenicArea: computed(() => identity.presence === 'IN_SCENIC'),
    initialize,
    login,
    logout,
    sendSmsCode,
    setPresence
  }
}
