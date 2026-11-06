<template>
  <div class="placeholder-page">
    <div class="glass-card">
      <div class="icon-wrapper">
        <div class="pulse-ring"></div>
        <el-icon class="status-icon"><InfoFilled /></el-icon>
      </div>
      <h2>{{ title }}</h2>
      <p class="subtitle">开发中</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'

const route = useRoute()

const title = computed(() => {
  return route.meta?.title || '待开发页面'
})
</script>

<style scoped>
.placeholder-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 64px);
  padding: 24px;
}

.glass-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 72px;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 20px 56px rgba(15, 23, 42, 0.08);
  max-width: 420px;
  width: 100%;
  text-align: center;
}

.icon-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 24px;
}

.status-icon {
  font-size: 48px;
  color: #0f766e;
  z-index: 2;
}

.pulse-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #ccfbf1;
  animation: pulse-animation 2s infinite ease-in-out;
  z-index: 1;
}

@keyframes pulse-animation {
  0% {
    transform: scale(0.9);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.2);
    opacity: 0.3;
  }
  100% {
    transform: scale(0.9);
    opacity: 0.8;
  }
}

.glass-card h2 {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 12px;
}

.subtitle {
  font-size: 15px;
  color: #64748b;
  margin: 0;
}
</style>
