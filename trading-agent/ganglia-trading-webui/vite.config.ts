/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api/traces': {
        target: 'http://localhost:9081',
        changeOrigin: true,
      },
      '/ws/traces': {
        target: 'ws://localhost:9081',
        ws: true,
      },
      '/api': {
        target: 'http://localhost:9080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:9080',
        ws: true,
      },
    },
  },
  build: {
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, 'index.html'),
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
    },
  },
});
