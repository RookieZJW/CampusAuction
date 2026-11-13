import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期通过代理转发到本地 Spring Boot，避免跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
