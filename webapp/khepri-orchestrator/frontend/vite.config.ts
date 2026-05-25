import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig({
  plugins: [vue(), vueJsx(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: '../static/dist',
    emptyOutDir: true,
    manifest: true,          
  },
  server: {
    port: 5173,
    proxy: {
      '/rest': {
        target: 'https://localhost:8443',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path,
      }
    }
  }
})
