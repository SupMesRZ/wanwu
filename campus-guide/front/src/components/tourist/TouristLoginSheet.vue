<template>
  <el-drawer :model-value="modelValue" direction="btt" size="auto" :show-close="false" class="tourist-login-drawer" @close="$emit('update:modelValue', false)">
    <div class="login-sheet">
      <div class="sheet-handle"></div>
      <header>
        <div><span>解锁完整导航</span><h2>登录后，为你规划专属路线</h2></div>
        <button type="button" aria-label="关闭" @click="$emit('update:modelValue', false)">×</button>
      </header>
      <form @submit.prevent="submit">
        <label><span>手机号</span><input v-model.trim="phone" inputmode="tel" maxlength="11" placeholder="请输入手机号" /></label>
        <label><span>验证码</span><div class="code-row"><input v-model.trim="smsCode" inputmode="numeric" maxlength="6" placeholder="6 位验证码" /><button type="button" :disabled="sending || countdown > 0 || !validPhone" @click="sendCode">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</button></div></label>
        <p v-if="errorText" class="error-text">{{ errorText }}</p>
        <button class="submit-button" type="submit" :disabled="loading || !validForm">{{ loading ? '登录中…' : '登录并继续' }}</button>
      </form>
      <p class="privacy">登录即表示同意用于保存路线偏好与导航记录</p>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'

const props = defineProps({ modelValue: Boolean, login: { type: Function, required: true }, sendSms: { type: Function, required: true } })
const emit = defineEmits(['update:modelValue', 'success'])
const phone = ref('')
const smsCode = ref('')
const sending = ref(false)
const loading = ref(false)
const countdown = ref(0)
const errorText = ref('')
let timer = null

const validPhone = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const validForm = computed(() => validPhone.value && /^\d{6}$/.test(smsCode.value))

const sendCode = async () => {
  if (!validPhone.value) return
  sending.value = true
  errorText.value = ''
  try {
    const response = await props.sendSms(phone.value)
    // 定档：不接真实短信，验证码由响应回显直接填入
    if (response.data?.displaySmsCode) smsCode.value = response.data.displaySmsCode
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
  } catch (error) {
    errorText.value = error?.response?.data?.message || '验证码发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

const submit = async () => {
  if (!validForm.value) return
  loading.value = true
  errorText.value = ''
  try {
    await props.login(phone.value, smsCode.value)
    emit('success')
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = error?.response?.data?.message || '登录失败，请检查验证码'
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.login-sheet { width: min(100%, 430px); margin: 0 auto; padding: 8px 20px calc(18px + env(safe-area-inset-bottom)); color: #16251f; }
.sheet-handle { width: 36px; height: 4px; margin: 0 auto 18px; border-radius: 10px; background: #d6ddd9; }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
header span { color: #18806b; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
header h2 { margin: 5px 0 20px; font-size: 22px; letter-spacing: -.03em; }
header button { border: 0; background: transparent; color: #829089; font-size: 28px; line-height: 1; }
form { display: grid; gap: 14px; }
label { display: grid; gap: 7px; color: #66736d; font-size: 12px; font-weight: 700; }
input { width: 100%; height: 50px; box-sizing: border-box; border: 0; border-radius: 12px; padding: 0 14px; background: #f3f6f4; color: #17241f; font-size: 16px; outline: none; }
input:focus { box-shadow: inset 0 0 0 2px rgba(24,128,107,.28); }
.code-row { display: grid; grid-template-columns: 1fr 112px; gap: 9px; }
.code-row button { border: 0; border-radius: 12px; background: #e5f1ed; color: #17725f; font-weight: 800; }
.submit-button { height: 52px; border: 0; border-radius: 14px; background: #187d69; color: #fff; font-size: 15px; font-weight: 800; }
.submit-button:disabled, .code-row button:disabled { opacity: .45; }
.error-text { margin: -3px 0; color: #d94f45; font-size: 12px; }
.privacy { margin: 14px 0 0; color: #9aa59f; text-align: center; font-size: 11px; }
</style>
