/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';
// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },
    server: {
        port: 5173,
        proxy: {
            '/api/traces': {
                target: 'http://localhost:8081',
                changeOrigin: true,
            },
            '/ws/traces': {
                target: 'ws://localhost:8081',
                ws: true,
            },
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/ws': {
                target: 'ws://localhost:8080',
                ws: true,
            },
        },
    },
    build: {
        rollupOptions: {
            input: {
                main: path.resolve(__dirname, 'index.html'),
                trace: path.resolve(__dirname, 'trace.html'),
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
