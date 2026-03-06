import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
  ],
  define: {
    global: 'window',
  },
  server: {
    port: 5173,
    host: 'localhost', // Use localhost explicitly
    strictPort: true,
    hmr: {
      protocol: 'ws',
      host: 'localhost',
      port: 5173,
    },
    watch: {
      usePolling: true,
    },
  },
  test: {
    environment: 'jsdom',
  },
})
