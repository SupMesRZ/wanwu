<template>
  <section class="dialog-sheet" :class="{ focused: hasConversation }">
    <header v-if="!hasConversation" class="sheet-head">
      <h1>今天想怎么玩？</h1>
      <span v-if="busy" class="thinking" aria-label="AI 正在回复"><i></i><i></i><i></i></span>
    </header>

    <div v-if="!hasConversation" class="quick-actions" aria-label="快捷提问">
      <button type="button" @click="emit('recommend')">
        <span class="quick-icon route"><el-icon><Guide /></el-icon></span>
        <strong>规划路线</strong>
      </button>
      <button type="button" @click="emit('quick', '附近有什么值得看的景点？')">
        <span class="quick-icon spot"><el-icon><Location /></el-icon></span>
        <strong>附近景点</strong>
      </button>
      <button type="button" @click="emit('quick', '厕所、餐饮和游客中心在哪里？')">
        <span class="quick-icon service"><el-icon><Coffee /></el-icon></span>
        <strong>找服务</strong>
      </button>
    </div>

    <div ref="messageListEl" class="message-list" role="log" aria-live="polite">
      <article v-for="message in visibleMessages" :key="message.id" :class="['message-item', message.sender]">
        <div v-if="message.sender === 'agent'" class="agent-dot">桃</div>
        <div class="message-bubble">
          <div v-if="message.sender === 'agent' && message.content" class="markdown-body" v-html="renderMarkdown(message.content)"></div>
          <div v-else-if="message.sender === 'agent' && message.status" class="progress-bubble">
            <span class="typing-bubble"><i></i><i></i><i></i></span>
            <span>{{ message.status }}</span>
            <small>{{ message.waitSeconds || 0 }} 秒</small>
          </div>
          <span v-else-if="message.sender === 'agent'" class="typing-bubble"><i></i><i></i><i></i></span>
          <p v-else>{{ message.content }}</p>
        </div>
      </article>

      <article v-if="currentRoute" class="route-result">
        <div><span>{{ routeResultLabel }}</span><strong>{{ currentRoute.name }}</strong><small>约 {{ currentRoute.estimatedDurationMin || '--' }} 分钟</small></div>
        <button type="button" @click="emit('open-map')">查看地图</button>
      </article>
    </div>

    <form class="composer" @submit.prevent="emit('send')">
      <button
        type="button"
        class="mic-button"
        :class="{ recording }"
        :disabled="busy || !sessionReady || transcribing"
        aria-label="按住说话"
        @pointerdown.prevent="startRecording"
        @pointerup.prevent="stopRecording"
        @pointerleave.prevent="cancelIfRecording"
        @pointercancel.prevent="cancelIfRecording"
        @contextmenu.prevent
      >
        <el-icon><Microphone /></el-icon>
      </button>
      <input :value="input" :disabled="busy || !sessionReady" :placeholder="composerPlaceholder" maxlength="1000" @input="emit('update:input', $event.target.value)" />
      <button type="submit" :disabled="busy || !input.trim() || !sessionReady" aria-label="发送"><el-icon><Promotion /></el-icon></button>
    </form>
    <div v-if="recording || transcribing" class="voice-hint">
      {{ recording ? `正在聆听 ${recordSeconds}s，松开发送` : '识别中…' }}
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Coffee, Guide, Location, Microphone, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import touristRequest from '../../utils/touristRequest'
import { blobToWav16k } from '../../utils/audioWav'

marked.setOptions({ breaks: true, gfm: true })
const props = defineProps({
  input: { type: String, default: '' }, messages: { type: Array, default: () => [] }, busy: Boolean,
  sessionReady: Boolean, currentRoute: { type: Object, default: null }, isAuthenticated: Boolean,
  isInScenicArea: Boolean, routeActionText: { type: String, default: '' }
})
const emit = defineEmits(['update:input', 'send', 'quick', 'recommend', 'route-action', 'open-map', 'recording-change'])
const messageListEl = ref(null)
let scrollFrame = 0
const hasConversation = computed(() => props.messages.some(message => message.sender === 'tourist'))
const routeResultLabel = computed(() => ({ PRESET: '景区精选', POINT_TO_POINT: '算路结果' })[props.currentRoute?.routeType] || '为你推荐')
const visibleMessages = computed(() => hasConversation.value ? props.messages.slice(1) : [])
const messageSignature = computed(() => visibleMessages.value.map(message => `${message.id}:${message.content?.length || 0}`).join('|'))
const renderMarkdown = content => DOMPurify.sanitize(marked.parse(content || ''), {
  USE_PROFILES: { html: true }, FORBID_TAGS: ['style', 'iframe', 'form'], FORBID_ATTR: ['style', 'onerror', 'onclick']
})

// ========== 按住说话 ==========

const MAX_RECORD_SECONDS = 60
const MIN_RECORD_BYTES = 2000

const recording = ref(false)
const transcribing = ref(false)
const recordSeconds = ref(0)
let mediaRecorder = null
let mediaStream = null
let recordChunks = []
let recordTimer = 0
let recordStartedAt = 0
let recordCancelled = false

const composerPlaceholder = computed(() => {
  if (recording.value) return '松开发送语音'
  return '问景点、路线或服务设施'
})

const startRecording = async () => {
  if (recording.value || transcribing.value || props.busy || !props.sessionReady) return
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    // 非安全上下文（http 直连 IP）或老浏览器没有录音能力，明确回落文字输入
    ElMessage.warning('当前环境不支持语音输入，请直接打字')
    return
  }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch {
    ElMessage.warning('没有拿到麦克风权限，请检查授权或直接打字')
    return
  }
  recordCancelled = false
  recordChunks = []
  recordStartedAt = Date.now()
  recordSeconds.value = 0
  mediaRecorder = new MediaRecorder(mediaStream)
  mediaRecorder.ondataavailable = event => {
    if (event.data?.size) recordChunks.push(event.data)
  }
  mediaRecorder.onstop = handleRecordStop
  mediaRecorder.start()
  recording.value = true
  emit('recording-change', true)
  recordTimer = window.setInterval(() => {
    recordSeconds.value = Math.floor((Date.now() - recordStartedAt) / 1000)
    if (recordSeconds.value >= MAX_RECORD_SECONDS) stopRecording()
  }, 250)
}

const stopRecording = () => {
  if (!recording.value) return
  finishRecording(false)
}

const cancelIfRecording = () => {
  if (!recording.value) return
  finishRecording(true)
}

const finishRecording = (cancelled) => {
  recordCancelled = cancelled
  recording.value = false
  emit('recording-change', false)
  window.clearInterval(recordTimer)
  recordTimer = 0
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  } else {
    releaseStream()
  }
}

const releaseStream = () => {
  mediaStream?.getTracks().forEach(track => track.stop())
  mediaStream = null
  mediaRecorder = null
}

const handleRecordStop = async () => {
  const chunks = recordChunks
  const cancelled = recordCancelled
  recordChunks = []
  releaseStream()
  if (cancelled) return
  const blob = new Blob(chunks, { type: chunks[0]?.type || 'audio/webm' })
  if (blob.size < MIN_RECORD_BYTES) {
    // 多半是误触，太短送识别也是浪费一次调用
    ElMessage.info('说话时间太短，按住麦克风再试一次')
    return
  }
  transcribing.value = true
  try {
    const wav = await blobToWav16k(blob)
    const form = new FormData()
    form.append('audio', new Blob([wav], { type: 'audio/wav' }), 'voice.wav')
    const res = await touristRequest.post('/tourist/guide/asr', form)
    const text = res?.data?.text?.trim()
    if (!text) {
      ElMessage.info('没有听清，请再说一次，或直接打字')
      return
    }
    emit('update:input', text)
  } catch (error) {
    // 语音链路任何一环失败都要明确回落到文字输入，不能静默
    ElMessage.warning(error?.response?.data?.message || '语音识别失败，请直接打字')
  } finally {
    transcribing.value = false
  }
}

const scrollToBottom = async () => {
  await nextTick()
  window.cancelAnimationFrame(scrollFrame)
  scrollFrame = window.requestAnimationFrame(() => {
    if (messageListEl.value) messageListEl.value.scrollTop = messageListEl.value.scrollHeight
  })
}
watch(messageSignature, scrollToBottom, { flush: 'post' })
watch(() => props.currentRoute, scrollToBottom, { flush: 'post' })
onMounted(scrollToBottom)
onBeforeUnmount(() => {
  window.cancelAnimationFrame(scrollFrame)
  window.clearInterval(recordTimer)
  releaseStream()
})
defineExpose({ scrollToBottom })
</script>

<style scoped>
.dialog-sheet { position:relative; min-height:0; height:100%; display:grid; grid-template-rows:auto auto minmax(0,1fr) auto; overflow:hidden; background:linear-gradient(180deg,transparent 0%,transparent 44%,rgba(238,246,241,.48) 61%,rgba(244,248,245,.9) 82%,rgba(247,250,248,.97) 100%); }
.dialog-sheet.focused { grid-template-rows:minmax(0,1fr) auto; background:linear-gradient(180deg,transparent 0%,transparent 34%,rgba(235,244,239,.2) 46%,rgba(240,247,243,.82) 76%,rgba(247,250,248,.98) 100%); }
.sheet-head { display:flex; align-items:flex-end; justify-content:space-between; margin-top:50dvh; padding:18px 17px 10px; }
.sheet-head h1 { margin:0; font-size:22px; letter-spacing:-.04em; }
.thinking,.typing-bubble { display: flex; align-items: center; gap: 4px; }.thinking { height: 22px; }.thinking i,.typing-bubble i { width: 5px; height: 5px; border-radius: 50%; background: #187d69; animation: think 1s ease-in-out infinite; }.thinking i:nth-child(2),.typing-bubble i:nth-child(2) { animation-delay: .12s; }.thinking i:nth-child(3),.typing-bubble i:nth-child(3) { animation-delay: .24s; }
.quick-actions { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 7px; padding: 0 13px 10px; }.quick-actions button { min-width: 0; min-height: 52px; display: flex; align-items: center; justify-content: center; gap: 7px; border: 0; border-radius: 14px; padding: 8px; background: #fff; color: #23342e; box-shadow: 0 4px 15px rgba(27,54,46,.055); }.quick-actions strong { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 11px; }.quick-icon { flex: 0 0 27px; width: 27px; height: 27px; display: grid; place-items: center; border-radius: 9px; color: #fff; font-size: 14px; }.quick-icon.route { background:#247a68 }.quick-icon.spot { background:#d98746 }.quick-icon.service { background:#6682a0 }
.message-list { min-height:0; overflow-y:auto; overscroll-behavior:contain; scrollbar-width:none; display:flex; flex-direction:column; gap:10px; padding:6px 13px 14px; scroll-padding-top:8px }.message-list::-webkit-scrollbar { display:none }.focused .message-list { align-self:end; width:100%; height:min(50dvh,460px); padding-top:8px }.message-item { display:flex; align-items:flex-end; gap:7px; flex:0 0 auto }.message-item.tourist { justify-content:flex-end }.agent-dot { flex:0 0 25px; width:25px; height:25px; display:grid; place-items:center; border-radius:8px; background:rgba(226,239,234,.72); color:#187d69; font-family:serif; font-size:11px; font-weight:900; backdrop-filter:blur(16px) }.message-bubble { max-width:min(84%,320px); max-height:min(42dvh,360px); min-width:34px; border:1px solid rgba(255,255,255,.5); border-radius:15px 15px 15px 5px; padding:9px 12px; overflow-x:hidden; overflow-y:auto; overscroll-behavior:contain; scrollbar-width:thin; scrollbar-color:rgba(23,111,93,.26) transparent; overflow-wrap:anywhere; background:rgba(255,255,255,.66); box-shadow:0 7px 24px rgba(24,50,42,.08); backdrop-filter:blur(22px) saturate(1.12); font-size:13px; line-height:1.62 }.message-bubble::-webkit-scrollbar { width:4px }.message-bubble::-webkit-scrollbar-thumb { border-radius:4px; background:rgba(23,111,93,.26) }.tourist .message-bubble { border-color:rgba(255,255,255,.12); border-radius:15px 15px 5px 15px; background:rgba(18,105,87,.84); color:#fff; box-shadow:0 6px 20px rgba(17,92,76,.18) }.message-bubble p { margin:0 }.typing-bubble { min-height:20px; justify-content:center }.progress-bubble { display:flex; align-items:center; gap:8px; min-width:190px; color:#31574c }.progress-bubble .typing-bubble { flex:none }.progress-bubble small { margin-left:auto; color:#7b8b84; white-space:nowrap; font-size:10px }.markdown-body :deep(p) { margin:0 0 7px }.markdown-body :deep(p:last-child) { margin-bottom:0 }.markdown-body :deep(ul),.markdown-body :deep(ol) { margin:6px 0; padding-left:18px }.markdown-body :deep(strong),.markdown-body :deep(a) { color:#126b59 }
.route-result { flex:0 0 auto; margin-left:32px; display:flex; align-items:center; justify-content:space-between; gap:12px; border:1px solid rgba(255,255,255,.5); border-radius:16px; padding:11px 12px; background:rgba(242,248,244,.7); box-shadow:0 8px 24px rgba(32,63,53,.08); backdrop-filter:blur(20px) }.route-result div { min-width:0; display:grid; gap:2px }.route-result span { color:#17806a; font-size:9px; font-weight:900 }.route-result strong { overflow:hidden; white-space:nowrap; text-overflow:ellipsis; font-size:14px }.route-result small { color:#73817b; font-size:10px }.route-result button { flex:0 0 auto; height:34px; border:0; border-radius:11px; padding:0 12px; background:#187d69; color:#fff; font-size:12px; font-weight:800 }
.composer { display:grid; grid-template-columns:44px minmax(0,1fr) 44px; gap:8px; padding:9px 13px calc(9px + env(safe-area-inset-bottom)); border-top:1px solid rgba(32,64,54,.08); background:rgba(248,250,248,.78); backdrop-filter:blur(20px) }.composer input { min-width:0; height:44px; border:0; border-radius:14px; padding:0 14px; background:rgba(255,255,255,.8); color:#17241f; font-size:13px; outline:none; box-shadow:inset 0 0 0 1px rgba(210,222,215,.9); backdrop-filter:blur(12px) }.composer input:focus { box-shadow:inset 0 0 0 2px rgba(24,125,105,.28) }.composer button { border:0; border-radius:14px; background:#187d69; color:#fff; font-size:17px }.composer button:disabled { opacity:.38 }
.composer .mic-button { background:#fff; color:#187d69; box-shadow:inset 0 0 0 1px rgba(24,125,105,.35); touch-action:none; user-select:none; -webkit-user-select:none }
.composer .mic-button.recording { background:#d9483b; color:#fff; box-shadow:none; animation:micPulse 1.2s ease-in-out infinite }
.voice-hint { position:absolute; left:0; right:0; bottom:calc(64px + env(safe-area-inset-bottom)); margin:0 auto; width:max-content; max-width:86%; padding:6px 14px; border-radius:12px; background:rgba(23,52,44,.86); color:#fff; font-size:12px; letter-spacing:.02em; backdrop-filter:blur(8px); pointer-events:none }
@keyframes micPulse { 0%,100% { transform:scale(1) } 50% { transform:scale(1.08) } }
@keyframes think { 0%,100% { transform:translateY(0); opacity:.35 } 50% { transform:translateY(-4px); opacity:1 } }
@media (max-width:350px) { .quick-actions { gap:5px; padding-inline:10px }.quick-actions button { padding:6px }.quick-icon { flex-basis:26px; width:26px; height:26px }.message-list,.composer { padding-inline:10px } }
</style>
