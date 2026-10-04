import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  return {
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: { proxy: { '/api': { target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true } } },
    test: { environment: 'jsdom', include: ['src/**/*.test.ts'] },
  }
})
