<template>
  <div class="dh-page">
    <section class="preview-panel">
      <div class="avatar-preview" :class="{ speaking: previewState === 'speaking' }">
        <Live2DPreview
          v-if="isLive2dModel"
          :model-url="form.avatarUrl"
          :state="previewState"
          :motion-urls="motionUrls"
          @ready="live2dReady = true"
          @error="live2dError = $event"
          @motion-change="handleMotionChange"
        />
        <img v-else-if="previewImageUrl" :src="previewImageUrl" alt="数字人头像" />
        <div v-else class="avatar-letter">{{ form.name.slice(0, 1) || '导' }}</div>
        <div class="voice-bars"><i></i><i></i><i></i><i></i></div>
      </div>
      <div class="preview-copy">
        <strong>{{ form.name || 'AI导游' }}</strong>
        <span>{{ previewStateText }}</span>
      </div>
      <div v-if="isLive2dModel" class="live2d-notice" :class="{ error: live2dError }">{{ live2dNotice }}</div>
      <el-segmented v-model="previewState" :options="previewStateOptions" />
      <el-button type="primary" @click="goVoiceSettings">配置声音</el-button>
    </section>

    <section class="config-panel">
      <header>
        <h2>形象与人设</h2>
        <p>形象资源、人设边界和状态动作均在此统一维护</p>
      </header>

      <el-form label-position="top" class="config-form">
        <el-form-item label="数字人名称">
          <el-input v-model="form.name" maxlength="80" />
        </el-form-item>
        <el-form-item label="形象封面或 Live2D 模型地址">
          <el-input v-model="form.avatarUrl" maxlength="500" />
        </el-form-item>
        <el-form-item label="待机动作 URL">
          <el-input v-model="form.actionIdleUrl" maxlength="500" />
        </el-form-item>
        <el-form-item label="聆听动作 URL">
          <el-input v-model="form.actionListeningUrl" maxlength="500" />
        </el-form-item>
        <el-form-item label="思考动作 URL">
          <el-input v-model="form.actionThinkingUrl" maxlength="500" />
        </el-form-item>
        <el-form-item label="播报动作 URL">
          <el-input v-model="form.actionSpeakingUrl" maxlength="500" />
        </el-form-item>
        <el-form-item class="wide" label="欢迎语">
          <el-input v-model="form.welcomeText" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item class="wide" label="人设提示词">
          <el-input v-model="form.personaPrompt" type="textarea" :rows="5" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button @click="loadProfile">重置</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存配置</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Live2DPreview from '@/components/Live2DPreview.vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const saving = ref(false)
const previewState = ref('idle')
const live2dReady = ref(false)
const live2dError = ref('')
const motionStatus = ref(null)

const previewStateOptions = [
  { label: '待机', value: 'idle' },
  { label: '聆听', value: 'listening' },
  { label: '思考', value: 'thinking' },
  { label: '播报', value: 'speaking' }
]

const form = reactive({
  name: '',
  avatarUrl: '',
  actionIdleUrl: '',
  actionListeningUrl: '',
  actionThinkingUrl: '',
  actionSpeakingUrl: '',
  personaPrompt: '',
  welcomeText: '',
  voiceCode: 'siyue',
  outputFormat: 'wav',
  sampleRate: 16000,
  speechRate: 0,
  pitchRate: 0,
  volume: 50,
  status: 1
})

const previewImageUrl = computed(() => {
  const stateUrl = {
    idle: form.actionIdleUrl,
    listening: form.actionListeningUrl,
    thinking: form.actionThinkingUrl,
    speaking: form.actionSpeakingUrl
  }[previewState.value]
  return stateUrl || (isLive2dModel.value ? '' : form.avatarUrl)
})

const isLive2dModel = computed(() => /\.model3\.json(?:$|\?)/i.test(form.avatarUrl || ''))
const motionUrls = computed(() => ({
  idle: form.actionIdleUrl,
  listening: form.actionListeningUrl,
  thinking: form.actionThinkingUrl,
  speaking: form.actionSpeakingUrl
}))

const live2dNotice = computed(() => {
  if (live2dError.value) return live2dError.value
  if (!live2dReady.value) return '正在加载 Live2D 模型资源'
  if (!motionStatus.value) return 'Live2D 模型与状态动作已就绪'
  if (motionStatus.value.error) return motionStatus.value.error
  const stateLabel = previewStateOptions.find(item => item.value === motionStatus.value.state)?.label
  const playState = motionStatus.value.started ? '动作已播放' : '动作未启动'
  return `${stateLabel || '当前'}${playState} · ${motionStatus.value.group}[${motionStatus.value.index}]`
})

const previewStateText = computed(() => {
  return previewStateOptions.find(item => item.value === previewState.value)?.label || '待机'
})

const loadProfile = async () => {
  live2dReady.value = false
  live2dError.value = ''
  motionStatus.value = null
  const res = await request.get('/admin/digital-human')
  if (res.code !== 'OK') {
    ElMessage.warning(res.message || '加载数字人配置失败')
    return
  }
  Object.assign(form, res.data)
}

const saveProfile = async () => {
  saving.value = true
  try {
    const res = await request.put('/admin/digital-human', form)
    if (res.code === 'OK') {
      Object.assign(form, res.data)
      ElMessage.success('数字人配置已保存')
    } else {
      ElMessage.warning(res.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

const goVoiceSettings = () => router.push('/digitalhuman/voices')
const handleMotionChange = event => { motionStatus.value = event }

onMounted(loadProfile)
</script>

<style scoped>
.dh-page {
  min-height: calc(100vh - 24px);
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
}

.preview-panel,
.config-panel {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.preview-panel {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 18px;
  padding: 18px;
}

.avatar-preview {
  width: 280px;
  height: 360px;
  display: grid;
  place-items: center;
  border: 1px solid #d7e5e1;
  border-radius: 12px;
  background: #f8fafc;
  overflow: hidden;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-letter {
  width: 120px;
  height: 120px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #0f766e;
  color: #ffffff;
  font-size: 52px;
  font-weight: 900;
}

.voice-bars {
  position: absolute;
  display: none;
  gap: 4px;
  margin-top: 220px;
}

.avatar-preview.speaking .voice-bars {
  display: flex;
}

.voice-bars i {
  width: 5px;
  height: 18px;
  border-radius: 999px;
  background: #0f766e;
  animation: bars 0.8s ease-in-out infinite;
}

.voice-bars i:nth-child(2) { animation-delay: 0.1s; }
.voice-bars i:nth-child(3) { animation-delay: 0.2s; }
.voice-bars i:nth-child(4) { animation-delay: 0.3s; }

.preview-copy {
  display: grid;
  gap: 4px;
  text-align: center;
}

.preview-copy strong {
  font-size: 18px;
}

.preview-copy span {
  color: #64748b;
  font-size: 13px;
}

.live2d-notice {
  max-width: 240px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

.live2d-notice.error {
  background: #fef2f2;
  color: #b91c1c;
}

.config-panel {
  padding: 18px;
}

.config-panel h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.config-panel p {
  margin: 0 0 16px;
  color: #64748b;
  font-size: 13px;
}

.config-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.config-form .wide {
  grid-column: 1 / -1;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@keyframes bars {
  0%, 100% { transform: scaleY(0.45); opacity: 0.45; }
  50% { transform: scaleY(1.25); opacity: 1; }
}

@media (max-width: 900px) {
  .dh-page,
  .config-form {
    grid-template-columns: 1fr;
  }
}
</style>
