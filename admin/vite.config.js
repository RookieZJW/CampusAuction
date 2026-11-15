import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期通过代理转发到本地 Spring Boot，避免跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 同时监听 IPv4/IPv6：避免只绑 [::1] 导致 127.0.0.1:5173 打不开
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
