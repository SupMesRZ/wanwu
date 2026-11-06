<template>
  <div ref="host" class="live2d-preview" role="img" aria-label="Live2D 数字人预览"></div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as PIXI from 'pixi.js'

const props = defineProps({
  modelUrl: { type: String, required: true },
  state: { type: String, default: 'idle' },
  active: { type: Boolean, default: true },
  mouthOpen: { type: Number, default: 0 },
  motionUrls: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['ready', 'error', 'motion-change'])
const host = ref(null)
let app = null
let model = null
let Live2DModel = null
let live2dConfig = null
let MotionPriority = null
let motionRequest = 0
let resizeObserver = null
let naturalWidth = 0
let naturalHeight = 0

const loadCubismCore = () => new Promise((resolve, reject) => {
  if (window.Live2DCubismCore) return resolve()
  const script = document.createElement('script')
  script.src = '/digital-human/live2dcubismcore.min.js'
  script.onload = resolve
  script.onerror = () => reject(new Error('Cubism Core 加载失败'))
  document.head.appendChild(script)
})

const defaultMotion = state => ({
  idle: { group: 'Idle', index: 0 },
  listening: { group: 'Idle', index: 1 },
  thinking: { group: 'TapBody', index: 1 },
  speaking: { group: 'TapBody', index: 0 }
}[state] || { group: 'Idle', index: 0 })

const normalizedPath = value => {
  const path = String(value || '').split(/[?#]/)[0]
  try {
    return decodeURIComponent(path).replace(/\\/g, '/')
  } catch {
    return path.replace(/\\/g, '/')
  }
}

const resolveMotion = state => {
  const configuredUrl = normalizedPath(props.motionUrls?.[state])
  const definitions = model?.internalModel?.settings?.motions || {}
  if (configuredUrl) {
    for (const [group, motions] of Object.entries(definitions)) {
      const index = (motions || []).findIndex(item => {
        const file = normalizedPath(item?.file)
        return file && (configuredUrl.endsWith(file) || file.endsWith(configuredUrl))
      })
      if (index >= 0) return { group, index, configuredUrl }
    }
  }
  return { ...defaultMotion(state), configuredUrl }
}

const playStateMotion = async () => {
  if (!model) return
  const request = ++motionRequest
  const target = resolveMotion(props.state)
  try {
    const started = await model.motion(target.group, target.index, MotionPriority?.FORCE ?? 3)
    if (request !== motionRequest) return
    emit('motion-change', {
      state: props.state,
      group: target.group,
      index: target.index,
      configured: Boolean(target.configuredUrl),
      started
    })
  } catch (error) {
    if (request === motionRequest) {
      emit('motion-change', {
        state: props.state,
        group: target.group,
        index: target.index,
        configured: Boolean(target.configuredUrl),
        started: false,
        error: error instanceof Error ? error.message : 'Live2D 动作播放失败'
      })
    }
  }
}

const resizeModel = () => {
  if (!app || !model || !host.value || !naturalWidth || !naturalHeight) return
  const width = host.value.clientWidth
  const height = host.value.clientHeight
  if (width < 2 || height < 2) return
  app.renderer.resize(width, height)
  const scale = Math.min((width * 0.92) / naturalWidth, (height * 0.98) / naturalHeight)
  model.scale.set(Number.isFinite(scale) && scale > 0 ? scale : 0.12)
  model.x = width / 2
  model.y = height * 0.99
}

const syncActivity = async () => {
  if (!app) return
  if (!props.active) {
    app.stop()
    return
  }
  app.start()
  await nextTick()
  window.requestAnimationFrame(resizeModel)
}

const applyMouth = () => {
  if (!model) return
  const value = Math.max(0, Math.min(1, Number(props.mouthOpen) || 0))
  try {
    model.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', value)
  } catch {}
}

onMounted(async () => {
  try {
    window.PIXI = PIXI
    await loadCubismCore()
    ;({ Live2DModel, MotionPriority, config: live2dConfig } = await import('pixi-live2d-display/cubism4'))
    live2dConfig.sound = false
    app = new PIXI.Application({ width: 1, height: 1, backgroundAlpha: 0, antialias: true, autoDensity: true, resolution: window.devicePixelRatio || 1 })
    host.value.appendChild(app.view)
    model = await Live2DModel.from(props.modelUrl, { autoInteract: false })
    model.anchor.set(0.5, 1)
    naturalWidth = model.width
    naturalHeight = model.height
    app.stage.addChild(model)
    resizeObserver = new ResizeObserver(resizeModel)
    resizeObserver.observe(host.value)
    resizeModel()
    model.internalModel.on('beforeModelUpdate', applyMouth)
    await syncActivity()
    playStateMotion()
    emit('ready')
  } catch (error) {
    emit('error', error instanceof Error ? error.message : 'Live2D 模型加载失败')
  }
})

watch([() => props.state, () => props.motionUrls], playStateMotion, { deep: true })
watch(() => props.active, syncActivity)
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  model?.internalModel?.off('beforeModelUpdate', applyMouth)
  model?.destroy()
  app?.destroy(true, { children: true, texture: true, baseTexture: true })
  model = null
  app = null
})
</script>

<style scoped>
.live2d-preview { width: 100%; height: 100%; display: grid; place-items: center; overflow: hidden; background: transparent; }
.live2d-preview :deep(canvas) { width: 100%; height: 100%; }
</style>
