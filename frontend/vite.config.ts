import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const ORACLE = 'http://192.168.8.244:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: ORACLE,
        changeOrigin: true,
      },
    },
  },
})
