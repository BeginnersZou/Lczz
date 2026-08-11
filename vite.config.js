import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      /**
       * 开发环境代理：所有以 /api 开头的请求转发到后端服务
       * 生产环境由 Nginx 反向代理 /api → 后端，前端无需修改
       * 不重写路径（保留 /api 前缀），与生产环境保持一致
       *
       * 对接后端时仅需修改 target 为后端实际地址
       */
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
