<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <div class="login-logo">
          <el-icon><Guide /></el-icon>
        </div>
        <h1>河大校园导航系统</h1>
        <p>河北大学七一路校区导航后台</p>
      </div>

      <el-alert
        v-if="errorMsg"
        :title="errorMsg"
        type="error"
        show-icon
        :closable="false"
        class="login-alert"
      />

      <el-form v-if="step === 1" label-position="top" @submit.prevent>
        <el-form-item label="管理账号">
          <el-input
            v-model="loginForm.username"
            size="large"
            :prefix-icon="User"
            placeholder="请输入管理员账号"
            :disabled="loading"
            @keyup.enter="handleNext"
          />
        </el-form-item>
        <el-form-item label="安全密码">
          <el-input
            v-model="loginForm.password"
            size="large"
            type="password"
            show-password
            :prefix-icon="Lock"
            placeholder="请输入账户密码"
            :disabled="loading"
            @keyup.enter="handleNext"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="handleNext">
          核验账号并发送 2FA 验证码
        </el-button>
        <div class="login-help">默认账号 root / root123456，可通过环境变量覆盖</div>
      </el-form>

      <el-form v-else label-position="top" @submit.prevent>
        <div class="sms-panel">
          <strong>双因子身份认证</strong>
          <span>验证码已发送至绑定手机</span>
          <button v-if="displaySmsCode" type="button" class="dev-sms-code" @click="fillDisplaySmsCode">
            本次验证码 {{ displaySmsCode }}
          </button>
        </div>
        <el-form-item label="6位手机验证码">
          <el-input
            v-model="loginForm.smsCode"
            size="large"
            maxlength="6"
            class="sms-input"
            placeholder="000000"
            :prefix-icon="Key"
            :disabled="loading"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <div class="sms-actions">
          <el-button :disabled="smsCountdown > 0 || loading" @click="resendSms">
            {{ smsCountdown > 0 ? `${smsCountdown} 秒后可重发` : '重新获取' }}
          </el-button>
          <el-button :disabled="loading" @click="goBack">返回修改账号</el-button>
        </div>
        <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="handleLogin">
          确认登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Guide, Key, Lock, User } from '@element-plus/icons-vue'
import request from '../utils/request'
import { hasPermission, loadCurrentUser } from '../utils/permission'

const emit = defineEmits(['login-success'])
const router = useRouter()

const step = ref(1)
const loading = ref(false)
const errorMsg = ref('')
const smsCountdown = ref(0)
const tempToken = ref('')
const displaySmsCode = ref('')
let timer = null

const loginForm = reactive({
  username: '',
  password: '',
  smsCode: ''
})

const handleNext = async () => {
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await request.post('/system/auth/admin/login', {
      username: loginForm.username,
      password: loginForm.password
    })
    if (res.code === 'OK') {
      tempToken.value = res.data.tempToken
      displaySmsCode.value = res.data.displaySmsCode || ''
      step.value = 2
      startCountdown()
    } else {
      errorMsg.value = res.message || '登录失败'
    }
  } catch (err) {
    errorMsg.value = '请求出错，请检查后台连接'
  } finally {
    loading.value = false
  }
}

const handleLogin = async () => {
  errorMsg.value = ''
  loading.value = true
  try {
      const res = await request.post('/system/auth/admin/sms-verify', {
        smsCode: loginForm.smsCode
      }, {
        headers: {
          'X-Temp-Token': tempToken.value
        }
      })
    if (res.code === 'OK') {
      localStorage.setItem('admin_token', res.data.accessToken)
      const username = res.data.username || loginForm.username || 'admin'
      localStorage.setItem('admin_username', username)
      // 身份和权限以 /admin/users/me 为准，不在前端假定登录的一定是 root
      await loadCurrentUser()
      emit('login-success')
      router.replace(hasPermission('scenic:read') ? '/scenic/map-workbench' : '/dashboard')
    } else {
      errorMsg.value = res.message || '短信核验失败'
    }
  } catch (err) {
    errorMsg.value = '服务器连接超时'
  } finally {
    loading.value = false
  }
}

const startCountdown = () => {
  smsCountdown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    if (smsCountdown.value > 0) {
      smsCountdown.value -= 1
    } else {
      clearInterval(timer)
    }
  }, 1000)
}

const resendSms = () => {
  errorMsg.value = ''
  displaySmsCode.value = ''
  startCountdown()
}

const goBack = () => {
  step.value = 1
  errorMsg.value = ''
  loginForm.smsCode = ''
  displaySmsCode.value = ''
  if (timer) clearInterval(timer)
}

const fillDisplaySmsCode = () => {
  loginForm.smsCode = displaySmsCode.value
}
</script>

<style scoped>
.login-container {
  width: min(420px, calc(100vw - 32px));
}

.login-card {
  border: 1px solid var(--border);
}

.login-header {
  text-align: center;
  margin-bottom: 24px;
}

.login-logo {
  width: 56px;
  height: 56px;
  display: inline-grid;
  place-items: center;
  margin-bottom: 14px;
  border-radius: 14px;
  color: #ffffff;
  background: linear-gradient(135deg, #0f766e, #1d4ed8);
}

.login-logo .el-icon {
  font-size: 28px;
}

.login-header h1 {
  margin: 0;
  font-size: 20px;
}

.login-header p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.login-alert {
  margin-bottom: 16px;
}

.login-submit {
  width: 100%;
  margin-top: 6px;
}

.login-help {
  margin-top: 14px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}

.sms-panel {
  display: grid;
  gap: 6px;
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--text-secondary);
  font-size: 13px;
}

.sms-panel strong {
  color: var(--text-main);
}

.dev-sms-code {
  width: fit-content;
  padding: 3px 8px;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  font: inherit;
  cursor: pointer;
}

.sms-input :deep(.el-input__inner) {
  text-align: center;
  letter-spacing: 6px;
  font-weight: 700;
}

.sms-actions {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
