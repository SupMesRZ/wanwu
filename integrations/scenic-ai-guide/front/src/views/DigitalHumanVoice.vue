<template>
  <main class="voice-page">
    <section class="voice-hero">
      <div><span>阿里云智能语音交互</span><h1>声音配置</h1><p>配置将直接用于游客端播报和 TTS 缓存键</p></div>
      <el-tag type="success" effect="light">ALIYUN NLS</el-tag>
    </section>
    <section class="voice-grid">
      <article class="settings-card">
        <h2>合成参数</h2>
        <el-form label-position="top" class="voice-form">
          <el-form-item label="音色"><el-select v-model="profile.voiceCode"><el-option v-for="item in options.voices" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="输出格式"><el-segmented v-model="profile.outputFormat" :options="options.outputFormats" /></el-form-item>
          <el-form-item label="采样率"><el-select v-model="profile.sampleRate"><el-option v-for="item in options.sampleRates" :key="item" :label="`${item} Hz`" :value="item" /></el-select></el-form-item>
          <el-form-item label="音量 {{ profile.volume }}"><el-slider v-model="profile.volume" :min="options.volume.min" :max="options.volume.max" /></el-form-item>
          <el-form-item label="语速 {{ profile.speechRate }}"><el-slider v-model="profile.speechRate" :min="options.speechRate.min" :max="options.speechRate.max" /></el-form-item>
          <el-form-item label="语调 {{ profile.pitchRate }}"><el-slider v-model="profile.pitchRate" :min="options.pitchRate.min" :max="options.pitchRate.max" /></el-form-item>
        </el-form>
        <div class="actions"><el-button @click="load">重置</el-button><el-button type="primary" :loading="saving" @click="save">保存声音配置</el-button></div>
      </article>
      <article class="test-card">
        <h2>试听与缓存预热</h2>
        <p>相同文本和相同声音参数将直接命中缓存</p>
        <el-input v-model="testText" type="textarea" :rows="5" maxlength="1000" show-word-limit />
        <div class="actions"><el-button type="primary" :loading="testing" @click="synthesize(testText)">试听</el-button><el-button :loading="warming" @click="warmCommonTexts">预热常用语</el-button></div>
        <div v-if="lastResult" class="result" :class="lastResult.cached ? 'hit' : ''">{{ lastResult.cached ? '缓存命中，已直接返回音频' : '阿里云合成完成，已写入缓存' }}</div>
        <div class="quick-texts"><button v-for="text in commonTexts" :key="text" type="button" @click="testText = text">{{ text }}</button></div>
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
const saving = ref(false), testing = ref(false), warming = ref(false), lastResult = ref(null)
const testText = ref('你好，欢迎来到景区，我是你的 AI 导游。')
const commonTexts = ['你好', '欢迎来到景区', '请问有什么可以帮您']
const profile = reactive({ name: '', avatarUrl: '', personaPrompt: '', welcomeText: '', actionIdleUrl: '', actionListeningUrl: '', actionThinkingUrl: '', actionSpeakingUrl: '', voiceCode: 'siyue', outputFormat: 'wav', sampleRate: 16000, speechRate: 0, pitchRate: 0, volume: 50, status: 1 })
const options = reactive({ voices: [], outputFormats: ['wav'], sampleRates: [16000], volume: { min: 0, max: 100 }, speechRate: { min: -500, max: 500 }, pitchRate: { min: -500, max: 500 } })
const load = async () => { const [profileRes, optionRes] = await Promise.all([request.get('/admin/digital-human'), request.get('/admin/digital-human/options')]); if (profileRes.code === 'OK') Object.assign(profile, profileRes.data); if (optionRes.code === 'OK') Object.assign(options, optionRes.data) }
const save = async () => { saving.value = true; try { const res = await request.put('/admin/digital-human', profile); if (res.code === 'OK') { Object.assign(profile, res.data); ElMessage.success('声音配置已保存') } else ElMessage.warning(res.message || '保存失败') } finally { saving.value = false } }
const synthesize = async text => { testing.value = true; try { const res = await request.post('/admin/digital-human/tts', { text }); if (res.code !== 'OK' || !res.data?.audioBase64) { ElMessage.warning(res.message || '试听失败'); return } lastResult.value = res.data; new Audio(`data:audio/${res.data.format || profile.outputFormat};base64,${res.data.audioBase64}`).play().catch(() => ElMessage.info('音频已生成，请检查浏览器播放权限')) } finally { testing.value = false } }
const warmCommonTexts = async () => { warming.value = true; try { for (const text of commonTexts) { const res = await request.post('/admin/digital-human/tts', { text }); if (res.code !== 'OK') throw new Error(res.message) } ElMessage.success('常用语已写入 TTS 缓存') } catch (error) { ElMessage.warning(error.message || '缓存预热失败') } finally { warming.value = false } }
onMounted(load)
</script>

<style scoped>
.voice-page{min-height:calc(100vh - 40px);padding:32px;background:#f6f8fb}.voice-hero{display:flex;justify-content:space-between;align-items:flex-start;padding:8px 0 28px}.voice-hero span{color:#0f766e;font-size:12px;font-weight:800;letter-spacing:.08em}.voice-hero h1{margin:7px 0;font-size:28px}.voice-hero p,.test-card p{margin:0;color:#64748b;font-size:13px}.voice-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(320px,.8fr);gap:18px}.settings-card,.test-card{padding:24px;border-radius:12px;background:#fff}.settings-card h2,.test-card h2{margin:0 0 20px;font-size:17px}.voice-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.result{margin-top:18px;padding:12px;border-radius:8px;background:#eff6ff;color:#1d4ed8;font-size:13px}.result.hit{background:#ecfdf5;color:#047857}.quick-texts{display:flex;flex-wrap:wrap;gap:8px;margin-top:16px}.quick-texts button{border:0;border-radius:999px;background:#f1f5f9;padding:7px 10px;color:#475569;cursor:pointer}@media(max-width:800px){.voice-page{padding:20px}.voice-grid,.voice-form{grid-template-columns:1fr}}
</style>
