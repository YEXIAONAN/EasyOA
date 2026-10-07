import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * EasyOA 前端构建配置。
 *
 * 开发模式下 /api 与 /actuator 代理到本地后端（localhost:8080）：
 * 浏览器始终与前端同源，Cookie / CSRF 行为与生产环境（Nginx 同源）完全一致，
 * 从而避免任何跨域与 Cookie 放宽配置带来的安全隐患。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.EASYOA_DEV_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
      '/actuator': {
        target: process.env.EASYOA_DEV_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Element Plus 采用全量引入（内部系统优先保证稳定性与一致性），
    // 已通过 vendor 分包独立缓存；如需进一步瘦身可在后续阶段切换按需引入。
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // 依赖分包：框架与组件库变动频率低，独立缓存可显著提升二次访问速度
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-axios': ['axios'],
        },
      },
    },
  },
})