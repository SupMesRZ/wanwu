import { createApp } from 'vue'
import { ElLoading } from 'element-plus'
import App from './App.vue'
import router from './router'
import 'element-plus/dist/index.css'
import './style.css'

const app = createApp(App)
// 组件由 unplugin-vue-components 按需自动注册，v-loading 是指令不在其覆盖范围，单独显式注册
app.directive('loading', ElLoading.directive)
app.use(router)
app.mount('#app')
