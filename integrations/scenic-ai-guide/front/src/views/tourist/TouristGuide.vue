<template>
  <main class="guide-page" :class="[{ chatting: hasConversation }, `view-${activeView}`]">
    <section class="scene-panel" :class="{ active: activeView === 'guide' }">
      <TouristRouteMap
        class="ambient-map"
        :route="currentRoute"
        :scenic-map="scenicMap"
        :visitor-location="lastLocation"
        :spot-lookup="spotLookup"
        :location-state="locationState"
      />
      <div class="scene-shade"></div>

      <div class="avatar-wrap" :class="avatarState">
        <Live2DPreview
          v-if="live2dModelUrl && !live2dFailed"
          :model-url="live2dModelUrl"
          :active="activeView === 'guide'"
          :state="avatarState"
          :mouth-open="mouthOpen"
          :motion-urls="motionUrls"
          @error="live2dFailed = true"
        />
        <div v-else class="avatar-fallback">{{ (profile.name || '桃').slice(0, 1) }}</div>
      </div>

      <div class="scene-actions">
        <button class="sound-button" :class="{ blocked: audioBlocked }" type="button" :aria-label="soundButtonLabel" @click="toggleSound">
          <el-icon><Mute v-if="!soundEnabled" /><Microphone v-else /></el-icon>
        </button>
        <button class="location-button" type="button" @click="requestLiveGuide">
          <el-icon><LocationInformation /></el-icon>
          <span>{{ locationActionText }}</span>
        </button>
      </div>

    </section>

    <section class="map-panel" :class="{ active: activeView === 'map' }">
      <TouristRouteMap
        :route="currentRoute"
        :scenic-map="scenicMap"
        :visitor-location="lastLocation"
        :spot-lookup="spotLookup"
        :location-state="locationState"
      />
      <div class="map-shade"></div>
      <article v-if="currentRoute" class="map-route-card">
        <header>
          <div><span>当前路线</span><strong>{{ currentRoute.name }}</strong></div>
          <small>{{ routeProgressText }}</small>
        </header>
        <ol>
          <li v-for="(node, index) in currentRoute.nodes" :key="node.id"
              :class="{ done: isRouteNodeDone(node), next: isRouteNodeNext(node) }">
            <i>{{ isRouteNodeDone(node) ? '✓' : index + 1 }}</i><span>{{ node.name }}</span>
          </li>
        </ol>
        <button type="button" :disabled="isAuthenticated && !isInScenicArea" @click="handleRouteAction">{{ routeActionText }}</button>
      </article>
      <div v-else class="map-empty">规划路线后，这里会显示完整游览路径</div>
    </section>

    <header class="guide-header">
      <div class="brand">
        <span class="brand-mark">桃</span>
        <strong>{{ profile.name || '小桃导游' }}</strong>
        <i class="brand-state" :aria-label="stateText"></i>
      </div>
      <button v-if="!isAuthenticated" class="identity-button" type="button" @click="showLogin = true">登录</button>
      <nav class="view-switch" aria-label="导览视图">
        <button type="button" :class="{ active: activeView === 'guide' }" @click="switchView('guide')">导游</button>
        <button type="button" :class="{ active: activeView === 'map' }" @click="switchView('map')">地图</button>
      </nav>
    </header>

    <TouristConversation
      v-show="activeView === 'guide'"
      class="conversation-overlay"
      ref="conversationEl"
      v-model:input="input"
      :messages="messages"
      :busy="busy"
      :session-ready="Boolean(sessionId)"
      :current-route="currentRoute"
      :is-authenticated="isAuthenticated"
      :is-in-scenic-area="isInScenicArea"
      :route-action-text="routeActionText"
      @send="sendMessage"
      @quick="sendQuick"
      @recommend="recommendRoute"
      @route-action="handleRouteAction"
      @open-map="switchView('map')"
      @recording-change="handleRecordingChange"
    />

    <TouristLoginSheet v-model="showLogin" :login="login" :send-sms="sendSmsCode" @success="handleLoginSuccess" />
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { LocationInformation, Microphone, Mute } from '@element-plus/icons-vue'
import Live2DPreview from '@/components/Live2DPreview.vue'
import TouristConversation from '@/components/tourist/TouristConversation.vue'
import TouristLoginSheet from '@/components/tourist/TouristLoginSheet.vue'
import TouristRouteMap from '@/components/tourist/TouristRouteMap.vue'
import { useTouristIdentity } from '@/composables/useTouristIdentity'
import touristRequest, { streamGuideMessage } from '@/utils/touristRequest'

// 会话 id 存在 sessionStorage，刷新可续接同一会话，关闭标签页则自然重开
const SESSION_STORAGE_KEY = 'tourist_guide_session_id'
const DEFAULT_WELCOME = '欢迎来到景区，我是你的 AI 导游小桃。'

const { identity, isAuthenticated, isInScenicArea, initialize, login, logout, sendSmsCode, setPresence } = useTouristIdentity()
const sessionId = ref(null)
const profile = ref({})
const messages = ref([])
const input = ref('')
const pendingIntent = ref('')
const busy = ref(false)
const avatarState = ref('idle')
const mouthOpen = ref(0)
const activeView = ref('guide')
const currentRoute = ref(null)
const routeProgress = ref(null)
const scenicMap = ref(null)
const lastLocation = ref(null)
const locationState = ref('idle')
const conversationEl = ref(null)
const showLogin = ref(false)
const live2dFailed = ref(false)
const soundEnabled = ref(true)
const audioBlocked = ref(false)
const activeAudio = shallowRef(null)
const audioQueue = []
let audioContext = null
let audioPlaybackRunning = false
let audioPlaybackGeneration = 0
let mouthFrame = 0
let stateResetTimer = 0
let responseProgressTimer = 0
const watchId = ref(null)
const reportingLocation = ref(false)
const lastReportAt = ref(0)
const locationTimeoutId = ref(null)

const live2dModelUrl = computed(() => profile.value.avatarUrl?.endsWith('.model3.json') ? profile.value.avatarUrl : '')
const motionUrls = computed(() => ({
  idle: profile.value.actionIdleUrl,
  listening: profile.value.actionListeningUrl,
  thinking: profile.value.actionThinkingUrl,
  speaking: profile.value.actionSpeakingUrl
}))
const spotLookup = computed(() => new Map((scenicMap.value?.payload?.spots || []).map(spot => [String(spot.id), spot])))
const hasConversation = computed(() => messages.value.some(message => message.sender === 'tourist'))
const stateText = computed(() => ({ idle: '在线', listening: '聆听中', thinking: '思考中', speaking: '正在讲解', error: '稍后再试' }[avatarState.value] || '在线'))
const soundButtonLabel = computed(() => audioBlocked.value ? '点击恢复语音' : soundEnabled.value ? '关闭语音' : '开启语音')
const locationActionText = computed(() => {
  if (!isAuthenticated.value) return '定位'
  if (locationState.value === 'watching') return '定位中'
  if (locationState.value === 'inside') return '景区内'
  if (locationState.value === 'outside') return '景区外'
  if (locationState.value === 'located') return '已定位'
  if (locationState.value === 'denied') return '未授权'
  if (locationState.value === 'error') return '重试定位'
  if (isInScenicArea.value) return '景区内'
  return '定位'
})
const routeActionText = computed(() => {
  if (!isAuthenticated.value) return '登录后生成专属路线'
  return isInScenicArea.value ? '开始实景导览' : '到达景区后开始导览'
})

const switchView = view => {
  activeView.value = view === 'map' ? 'map' : 'guide'
  if (activeView.value === 'guide' && soundEnabled.value && activeAudio.value) {
    resumeAudioContext()
  }
}

const bootstrap = async () => {
  busy.value = true
  try {
    await initialize()
    // 优先复用上次会话，刷新后不丢对话；会话已失效或不属于当前身份时再开新会话
    if (!(await resumeSession())) await startSession()
  } catch (error) {
    avatarState.value = 'error'
    addMessage('agent', apiErrorMessage(error, '导览初始化失败，请刷新页面重试'))
  } finally {
    busy.value = false
  }
}

const applySessionEnvelope = data => {
  sessionId.value = data.sessionId
  sessionStorage.setItem(SESSION_STORAGE_KEY, String(data.sessionId))
  profile.value = data.digitalHuman || {}
  live2dFailed.value = false
  currentRoute.value = null
  routeProgress.value = null
}

const resumeSession = async () => {
  const storedId = sessionStorage.getItem(SESSION_STORAGE_KEY)
  if (!storedId) return false
  try {
    const response = await touristRequest.get(`/tourist/guide/sessions/${storedId}`)
    if (response.code !== 'OK') return false
    applySessionEnvelope(response.data)
    messages.value = []
    await replayHistory(storedId, response.data.welcomeText)
    await loadTouristMap(response.data.scenicAreaId)
    return true
  } catch {
    sessionStorage.removeItem(SESSION_STORAGE_KEY)
    return false
  }
}

const replayHistory = async (targetSessionId, welcomeText) => {
  const response = await touristRequest.get(`/tourist/guide/sessions/${targetSessionId}/messages`)
  const history = response.code === 'OK' ? response.data || [] : []
  if (!history.length) {
    addMessage('agent', welcomeText || DEFAULT_WELCOME)
    return
  }
  history.forEach(item => {
    if (!item.content) return
    addMessage(item.sender === 'TOURIST' ? 'tourist' : 'agent', item.content)
  })
}

const startSession = async () => {
  const response = await touristRequest.post('/tourist/guide/sessions', {})
  if (response.code !== 'OK') throw new Error(response.message || '导览初始化失败')
  applySessionEnvelope(response.data)
  messages.value = []
  addMessage('agent', response.data.welcomeText || DEFAULT_WELCOME)
  await loadTouristMap(response.data.scenicAreaId)
}

const loadTouristMap = async scenicAreaId => {
  try {
    const response = await touristRequest.get(`/tourist/scenic-areas/${scenicAreaId || 1}/map`)
    scenicMap.value = response.code === 'OK' ? response.data : null
  } catch {
    scenicMap.value = null
  }
}

const recommendRoute = () => {
  if (!isAuthenticated.value) {
    showPublicPresetRoute()
    return
  }
  sendQuick('我想轻松游览 2 小时，请推荐一条适合我的路线', 'ROUTE_RECOMMEND')
}

const showPublicPresetRoute = () => {
  const preset = (scenicMap.value?.payload?.presetRoutes || [])
    .find(route => route.status == null || route.status === 1)
  if (!preset) {
    showLogin.value = true
    return
  }
  const nodes = (preset.spotIds || []).map((spotId, index) => {
    const spot = spotLookup.value.get(String(spotId)) || {}
    const point = Array.isArray(spot.labelPoint) ? spot.labelPoint : []
    return {
      id: spotId,
      name: spot.name || `景点 ${index + 1}`,
      sequence: index + 1,
      latitude: point[0],
      longitude: point[1]
    }
  })
  routeProgress.value = null
  currentRoute.value = {
    id: preset.id,
    name: preset.name,
    routeType: 'PRESET',
    estimatedDurationMin: preset.durationMin,
    recommendationReason: preset.recommendationNote || preset.description,
    nodes
  }
  addMessage('agent', preset.recommendationNote || preset.description || '这是景区为游客准备的精选路线。登录后，我还能根据你的时间和偏好重新规划。')
  scrollMessages()
}

const handleRouteAction = () => {
  if (!isAuthenticated.value) {
    showLogin.value = true
    return
  }
  requestLiveGuide()
}

const sendQuick = (text, intent = '') => {
  input.value = text
  pendingIntent.value = intent
  sendMessage()
}

const sendMessage = async () => {
  if (!input.value || !sessionId.value || busy.value) return
  await unlockAudioOutput()
  const content = input.value
  const intent = pendingIntent.value
  window.clearTimeout(stateResetTimer)
  stopAudioPlayback()
  input.value = ''
  pendingIntent.value = ''
  addMessage('tourist', content)
  const streamingMessage = addMessage('agent', '')
  streamingMessage.status = '正在理解你的需求'
  streamingMessage.waitSeconds = 0
  window.clearInterval(responseProgressTimer)
  responseProgressTimer = window.setInterval(() => {
    streamingMessage.waitSeconds += 1
  }, 1000)
  avatarState.value = 'thinking'
  busy.value = true
  try {
    let completedMessage = null
    await streamGuideMessage(sessionId.value, {
      content,
      intent,
      speechEnabled: soundEnabled.value
    }, event => {
      if (event.type === 'status') {
        streamingMessage.status = event.status || streamingMessage.status
        scrollMessages()
      } else if (event.type === 'delta') {
        streamingMessage.status = ''
        streamingMessage.content += event.delta || ''
        avatarState.value = 'speaking'
        scrollMessages()
      } else if (event.type === 'completed') {
        streamingMessage.status = ''
        completedMessage = event.message
        if (completedMessage?.answer) streamingMessage.content = completedMessage.answer
        if (completedMessage?.route) {
          currentRoute.value = completedMessage.route
          routeProgress.value = null
          scrollMessages()
        }
        if (completedMessage?.audioBase64 && soundEnabled.value) {
          enqueueAudio(completedMessage.audioBase64, profile.value.outputFormat, Number.MAX_SAFE_INTEGER)
        }
      } else if (event.type === 'audio') {
        enqueueAudio(event.audioBase64, event.audioFormat, event.sequence)
      } else if (event.type === 'error') {
        throw new Error(event.error || 'AI 导游暂时无法回答')
      }
    })
    if (!completedMessage) throw new Error('流式回答未正常完成')
    streamingMessage.content ||= completedMessage.answer || ''
    if (!activeAudio.value && !audioQueue.length) avatarState.value = completedMessage.actionState || 'speaking'
  } catch (error) {
    avatarState.value = 'error'
    streamingMessage.content ||= apiErrorMessage(error, '网络有点不稳定，请稍后再试')
  } finally {
    window.clearInterval(responseProgressTimer)
    responseProgressTimer = 0
    busy.value = false
    stateResetTimer = window.setTimeout(() => {
      if (!busy.value && avatarState.value !== 'error' && !activeAudio.value
        && (!audioQueue.length || audioBlocked.value)) avatarState.value = 'idle'
    }, 1800)
  }
}

const unlockAudioOutput = async () => {
  if (!soundEnabled.value) return false
  try {
    audioContext ||= new AudioContext()
    const running = await resumeAudioContext()
    audioBlocked.value = !running
    if (!running) return false
    const unlockSource = audioContext.createBufferSource()
    unlockSource.buffer = audioContext.createBuffer(1, 1, 22050)
    unlockSource.connect(audioContext.destination)
    unlockSource.start(0)
    return true
  } catch {
    audioBlocked.value = true
    return false
  }
}

const resumeAudioContext = async (timeout = 1200) => {
  if (audioContext?.state === 'running') return true
  let timer = 0
  try {
    await Promise.race([
      audioContext.resume(),
      new Promise(resolve => { timer = window.setTimeout(resolve, timeout) })
    ])
  } catch {
    return false
  } finally {
    window.clearTimeout(timer)
  }
  return audioContext.state === 'running'
}

const routeProgressText = computed(() => {
  if (!currentRoute.value) return ''
  const base = currentRoute.value.totalDistanceMeter
    ? `${currentRoute.value.totalDistanceMeter} 米 · `
    : ''
  const duration = `约 ${currentRoute.value.estimatedDurationMin || '--'} 分钟`
  const progress = routeProgress.value
  if (!progress || progress.routeId !== currentRoute.value.id) return base + duration
  if (progress.finished) return `${base}${duration} · 已完成全部 ${progress.totalNodes} 站`
  return `${base}${duration} · 第 ${progress.currentSequence}/${progress.totalNodes} 站 · 下一站 ${progress.nextNodeName}`
})

const isRouteNodeDone = node => {
  const progress = routeProgress.value
  return Boolean(progress && progress.routeId === currentRoute.value?.id
    && node.sequence != null && node.sequence <= progress.currentSequence)
}

const isRouteNodeNext = node => {
  const progress = routeProgress.value
  return Boolean(progress && !progress.finished && progress.routeId === currentRoute.value?.id
    && node.sequence === progress.currentSequence + 1)
}

const requestLiveGuide = () => {
  if (!isAuthenticated.value) {
    showLogin.value = true
    return
  }
  if (!navigator.geolocation) {
    locationState.value = 'unsupported'
    addMessage('agent', '当前浏览器不支持定位，你仍可使用文字导览。')
    return
  }
  if (watchId.value !== null) return
  locationState.value = 'watching'
  lastReportAt.value = 0
  watchId.value = navigator.geolocation.watchPosition(handlePosition, handlePositionError, {
    enableHighAccuracy: true,
    timeout: 10000,
    maximumAge: 5000
  })
  locationTimeoutId.value = window.setTimeout(() => {
    if (locationState.value !== 'watching' || lastReportAt.value > 0) return
    if (watchId.value !== null) navigator.geolocation.clearWatch(watchId.value)
    watchId.value = null
    locationState.value = 'error'
    addMessage('agent', '暂时没有获取到你的位置，请检查浏览器定位权限后重试。')
  }, 12000)
}

const handlePosition = async position => {
  if (!sessionId.value || reportingLocation.value || Date.now() - lastReportAt.value < 5000) return
  lastReportAt.value = Date.now()
  window.clearTimeout(locationTimeoutId.value)
  locationTimeoutId.value = null
  reportingLocation.value = true
  lastLocation.value = {
    lat: position.coords.latitude,
    lng: position.coords.longitude,
    accuracy: Math.round(position.coords.accuracy || 0)
  }
  try {
    const response = await touristRequest.post(`/tourist/guide/sessions/${sessionId.value}/locations`, {
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
      accuracyMeter: Math.round(position.coords.accuracy || 0),
      speedMeterSecond: position.coords.speed == null ? null : position.coords.speed,
      reportedAt: formatLocalDateTime(new Date(position.timestamp || Date.now()))
    })
    if (response.code !== 'OK') throw new Error(response.message)
    const inScenic = ['ENTERING', 'INSIDE', 'COOLDOWN'].includes(response.data.state)
    locationState.value = inScenic ? 'inside' : 'outside'
    setPresence(inScenic ? 'IN_SCENIC' : 'OUTSIDE')
    if (response.data.routeProgress) {
      routeProgress.value = response.data.routeProgress
    }
    if (response.data.guideMessage) {
      const guide = response.data.guideMessage
      avatarState.value = guide.actionState || 'speaking'
      addMessage('agent', guide.answer)
      if (soundEnabled.value) playAudio(guide.audioBase64, profile.value.outputFormat)
    }
  } catch (error) {
    locationState.value = 'error'
    addMessage('agent', apiErrorMessage(error, '位置确认失败，请稍后重试'))
  } finally {
    reportingLocation.value = false
  }
}

const handlePositionError = error => {
  window.clearTimeout(locationTimeoutId.value)
  locationTimeoutId.value = null
  if (watchId.value !== null && navigator.geolocation) navigator.geolocation.clearWatch(watchId.value)
  watchId.value = null
  locationState.value = error.code === error.PERMISSION_DENIED ? 'denied' : 'error'
  setPresence('OUTSIDE')
}

const handleLoginSuccess = async () => {
  stopLocationWatch()
  await startSession()
  addMessage('agent', '登录成功。告诉我你的游览时长和同行人群，我会为你规划专属路线。')
}

const handleLogout = async () => {
  stopLocationWatch()
  await logout()
  await startSession()
}

const stopLocationWatch = () => {
  window.clearTimeout(locationTimeoutId.value)
  locationTimeoutId.value = null
  if (watchId.value !== null && navigator.geolocation) navigator.geolocation.clearWatch(watchId.value)
  watchId.value = null
  locationState.value = 'idle'
  lastLocation.value = null
  setPresence('UNKNOWN')
}

const playAudio = (base64, format = 'wav') => {
  if (!base64) return
  stopAudioPlayback()
  enqueueAudio(base64, format, 1)
}

const enqueueAudio = (base64, format = 'wav', sequence = Number.MAX_SAFE_INTEGER) => {
  if (!soundEnabled.value || !base64) return
  audioQueue.push({ base64, format, sequence: Number(sequence) || Number.MAX_SAFE_INTEGER })
  audioQueue.sort((left, right) => left.sequence - right.sequence)
  playNextAudio()
}

const playNextAudio = async () => {
  if (audioPlaybackRunning || !soundEnabled.value || !audioQueue.length) return
  audioPlaybackRunning = true
  const generation = audioPlaybackGeneration
  try {
    while (soundEnabled.value && generation === audioPlaybackGeneration && audioQueue.length) {
      const item = audioQueue.shift()
      try {
        await playAudioItem(item, generation)
      } catch {
      }
      if (audioBlocked.value) break
    }
  } finally {
    audioPlaybackRunning = false
    if (!activeAudio.value) stopMouthAnimation()
    if (!busy.value && !audioQueue.length) avatarState.value = 'idle'
  }
}

const playAudioItem = async (item, generation) => {
  audioContext ||= new AudioContext()
  if (!(await resumeAudioContext())) {
    audioBlocked.value = true
    audioQueue.unshift(item)
    return
  }
  audioBlocked.value = false
  const binary = window.atob(item.base64)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index)
  const buffer = await audioContext.decodeAudioData(bytes.buffer)
  if (!soundEnabled.value || generation !== audioPlaybackGeneration) return
  const source = audioContext.createBufferSource()
  source.buffer = buffer
  source.connect(audioContext.destination)
  const playback = { source, buffer, startedAt: null, cancelled: false }
  activeAudio.value = playback
  avatarState.value = 'speaking'
  await new Promise(resolve => {
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      if (activeAudio.value === playback) activeAudio.value = null
      try { source.disconnect() } catch {}
      stopMouthAnimation()
      resolve()
    }
    source.onended = () => {
      finish()
    }
    source.start(0)
    playback.startedAt = audioContext.currentTime
    connectAudioMouth(playback)
    window.setTimeout(finish, Math.ceil((buffer.duration + 1) * 1000))
  })
}

const stopAudioPlayback = () => {
  audioPlaybackGeneration += 1
  audioQueue.length = 0
  if (activeAudio.value) {
    activeAudio.value.cancelled = true
    try { activeAudio.value.source?.stop() } catch {}
    try { activeAudio.value.source?.disconnect() } catch {}
  }
  activeAudio.value = null
  audioPlaybackRunning = false
  stopMouthAnimation()
}

const connectAudioMouth = playback => {
  stopMouthAnimation()
  try {
    const animate = () => {
      if (activeAudio.value !== playback) {
        mouthOpen.value = 0
        return
      }
      const average = getPlaybackVolume(playback)
      const target = Math.max(0, Math.min(1, (average - 0.012) * 4.8))
      mouthOpen.value += (target - mouthOpen.value) * 0.48
      mouthFrame = window.requestAnimationFrame(animate)
    }
    mouthFrame = window.requestAnimationFrame(animate)
  } catch {
    mouthOpen.value = avatarState.value === 'speaking' ? 0.34 : 0
  }
}

const getPlaybackVolume = playback => {
  const buffer = playback.buffer
  const channel = buffer?.getChannelData(0)
  if (!channel?.length || playback.startedAt == null) return 0
  const currentSample = Math.floor((audioContext.currentTime - playback.startedAt) * buffer.sampleRate)
  const windowSize = Math.max(1, Math.floor(buffer.sampleRate * 0.024))
  const start = Math.max(0, currentSample - Math.floor(windowSize / 2))
  const end = Math.min(channel.length, start + windowSize)
  if (start >= end) return 0
  let energy = 0
  for (let index = start; index < end; index++) energy += channel[index] * channel[index]
  return Math.sqrt(energy / (end - start))
}

const stopMouthAnimation = () => {
  window.cancelAnimationFrame(mouthFrame)
  mouthFrame = 0
  mouthOpen.value = 0
}

const toggleSound = async () => {
  if (soundEnabled.value && audioBlocked.value) {
    if (await unlockAudioOutput()) playNextAudio()
    return
  }
  soundEnabled.value = !soundEnabled.value
  if (!soundEnabled.value) {
    stopAudioPlayback()
    audioBlocked.value = false
    avatarState.value = busy.value ? 'thinking' : 'idle'
    return
  }
  if (await unlockAudioOutput()) playNextAudio()
}

const handleRecordingChange = (isRecording) => {
  // 录音期间进入 listening 态，结束后回到当时该处的状态：AI 在回复则 thinking，否则 idle
  avatarState.value = isRecording ? 'listening' : (busy.value ? 'thinking' : 'idle')
}

const addMessage = (sender, content) => {
  const message = { id: `${Date.now()}_${Math.random().toString(36).slice(2)}`, sender, content }
  messages.value.push(message)
  scrollMessages()
  return messages.value[messages.value.length - 1]
}

const scrollMessages = () => nextTick(() => conversationEl.value?.scrollToBottom())

const formatLocalDateTime = date => {
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const apiErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback

onMounted(bootstrap)
onBeforeUnmount(() => {
  stopLocationWatch()
  stopAudioPlayback()
  window.clearTimeout(stateResetTimer)
  window.clearInterval(responseProgressTimer)
  audioContext?.close().catch(() => {})
})
</script>

<style scoped>
.guide-page { --green:#176f5d; position:relative; width:100%; height:100dvh; min-height:100svh; overflow:hidden; background:#dcebe3; color:#17241f; font-family:"PingFang SC","Microsoft YaHei",sans-serif }
.scene-panel,.map-panel { position:absolute; inset:0; overflow:hidden; opacity:0; visibility:hidden; pointer-events:none; transition:opacity .18s ease }
.scene-panel.active,.map-panel.active { opacity:1; visibility:visible; pointer-events:auto }
.ambient-map { filter:blur(10px) saturate(.72); opacity:.58; transform:scale(1.08) }
.scene-shade { position:absolute; inset:0; background:radial-gradient(circle at 58% 28%,rgba(255,255,255,.34),transparent 43%),linear-gradient(180deg,rgba(21,69,56,.25),transparent 30%,rgba(231,241,235,.2) 62%,rgba(239,246,242,.72)); pointer-events:none }
.guide-header { position:absolute; z-index:12; top:0; left:0; right:0; display:flex; align-items:center; gap:12px; padding:max(10px,env(safe-area-inset-top)) 16px 10px; color:#fff }
.brand { display:flex; align-items:center; gap:9px; text-shadow:0 1px 9px rgba(0,0,0,.2) }
.brand-mark { width:34px; height:34px; display:grid; place-items:center; border-radius:11px; background:rgba(255,255,255,.9); color:var(--green); font-family:serif; font-size:18px; font-weight:900; box-shadow:0 6px 20px rgba(19,58,48,.14) }
.brand strong { font-size:14px; letter-spacing:.02em }.brand-state { width:7px; height:7px; border-radius:50%; background:#5ee0b3; box-shadow:0 0 0 3px rgba(255,255,255,.2) }
.identity-button { min-width:58px; height:34px; border:0; border-radius:11px; padding:0 12px; background:rgba(255,255,255,.9); color:#176d5b; font-weight:800; backdrop-filter:blur(14px) }
.view-switch { margin-left:auto; flex:none; display:flex; padding:3px; border:1px solid rgba(255,255,255,.4); border-radius:13px; background:rgba(20,64,52,.28); backdrop-filter:blur(18px) }
.view-switch button { width:48px; height:28px; border:0; border-radius:9px; background:transparent; color:rgba(255,255,255,.78); font-size:12px; font-weight:800 }.view-switch button.active { background:rgba(255,255,255,.92); color:var(--green); box-shadow:0 3px 10px rgba(20,55,45,.12) }
.view-switch button:focus-visible { outline:2px solid rgba(255,255,255,.9); outline-offset:1px }
.avatar-wrap { position:absolute; z-index:2; top:70px; left:50%; width:min(112vw,480px); height:calc(100dvh - 120px); pointer-events:none; filter:drop-shadow(0 22px 28px rgba(20,54,45,.16)); transform:translateX(-50%); transform-origin:center bottom }
.avatar-wrap :deep(.live2d-preview),.avatar-wrap :deep(canvas) { width:100% !important; height:100% !important }
.avatar-fallback { position:absolute; inset:24% 24% auto; aspect-ratio:1; display:grid; place-items:center; border-radius:50%; background:rgba(255,255,255,.88); color:var(--green); font-family:serif; font-size:64px; font-weight:900 }
.scene-actions { position:absolute; z-index:9; left:16px; top:calc(max(14px,env(safe-area-inset-top)) + 102px); display:flex; align-items:center; gap:8px }.scene-actions button { border:0; box-shadow:0 8px 24px rgba(25,57,48,.14); backdrop-filter:blur(16px) }
.sound-button { width:40px; height:40px; display:grid; place-items:center; border-radius:13px; background:rgba(255,255,255,.82); color:#31574c; font-size:17px }.location-button { height:40px; display:flex; align-items:center; gap:7px; border-radius:13px; padding:0 13px; background:rgba(23,111,93,.88); color:#fff; font-size:12px; font-weight:800 }
.sound-button.blocked { color:#b75642; box-shadow:0 0 0 3px rgba(183,86,66,.12) }
.conversation-overlay { position:absolute !important; z-index:7; inset:94px 0 0; height:auto !important }
.map-panel :deep(.route-map) { inset:0 }.map-shade { position:absolute; inset:0; pointer-events:none; background:linear-gradient(180deg,rgba(16,61,50,.32),transparent 24%,transparent 64%,rgba(20,49,41,.18)) }
.map-route-card { position:absolute; z-index:5; left:14px; right:14px; bottom:calc(14px + env(safe-area-inset-bottom)); border:1px solid rgba(255,255,255,.6); border-radius:20px; padding:15px; background:rgba(249,251,249,.86); box-shadow:0 14px 38px rgba(28,59,49,.16); backdrop-filter:blur(22px) }.map-route-card header { display:flex; align-items:flex-end; justify-content:space-between; gap:12px }.map-route-card header div { display:grid; gap:2px }.map-route-card header span { color:var(--green); font-size:10px; font-weight:900 }.map-route-card header strong { font-size:17px }.map-route-card header small { color:#687870; font-size:11px }.map-route-card ol { display:flex; gap:7px; overflow-x:auto; margin:12px 0; padding:0 0 3px; list-style:none; scrollbar-width:none }.map-route-card li { flex:0 0 auto; display:flex; align-items:center; gap:5px; border-radius:10px; padding:6px 8px; background:rgba(231,241,236,.9); font-size:11px; font-weight:700 }.map-route-card li i { width:18px; height:18px; display:grid; place-items:center; border-radius:50%; background:var(--green); color:#fff; font-size:9px; font-style:normal } .map-route-card li.done { opacity:.55 } .map-route-card li.done i { background:#8aa79c } .map-route-card li.next { outline:2px solid rgba(23,111,93,.45); outline-offset:-2px }.map-route-card>button { width:100%; height:40px; border:0; border-radius:12px; background:var(--green); color:#fff; font-weight:800 }.map-route-card>button:disabled { background:#cbd6d1; color:#6b7973 }.map-empty { position:absolute; z-index:5; left:50%; bottom:calc(26px + env(safe-area-inset-bottom)); width:max-content; max-width:calc(100% - 32px); border-radius:14px; padding:11px 14px; background:rgba(255,255,255,.82); color:#506159; font-size:12px; backdrop-filter:blur(18px); transform:translateX(-50%) }
@media (min-width: 720px) {
  .guide-page { width:430px; max-height:920px; margin:0 auto; box-shadow:0 0 50px rgba(27,54,46,.14); }
  body { background: #edf1ee; }
}
</style>

<style>
.tourist-login-drawer { border-radius: 24px 24px 0 0 !important; }
.tourist-login-drawer .el-drawer__header { display: none; }
.tourist-login-drawer .el-drawer__body { padding: 0; }
</style>
