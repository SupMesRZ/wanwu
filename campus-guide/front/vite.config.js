import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    // 样式走 main.js 的全量 CSS，避免逐组件样式与 dist/index.css 重复加载
    Components({ resolvers: [ElementPlusResolver({ importStyle: false })] })
  ],
  define: {
    __GAODE_API_KEY__: JSON.stringify(process.env.GAODE_API_KEY || ''),
    __GAODE_SECRET__: JSON.stringify(process.env.GAODE_SECRET || '')
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api/v1': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8086',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
