import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    build: {
      chunkSizeWarningLimit: 525,
      rolldownOptions: {
        output: {
          codeSplitting: {
            groups: [
              {
                name: 'echarts',
                test: /[\\/]node_modules[\\/]echarts[\\/]/,
                priority: 20
              },
              {
                name: 'zrender',
                test: /[\\/]node_modules[\\/]zrender[\\/]/,
                priority: 20
              }
            ]
          }
        }
      }
    },
    resolve: {
      alias: {
        '@': resolve(import.meta.dirname, 'src')
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      proxy: {
        /**
         * 开发环境代理：浏览器只请求 /api，由 Vite 转发到 Java 后端。
         * 本机/局域网地址写在不入库的 .env.local 中，避免把环境地址写死在源码。
         * 生产环境由 Nginx 反向代理 /api 到后端，不读取该开发代理配置。
         */
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true
        }
      }
    }
  }
})
