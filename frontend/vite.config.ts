import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 8880,
    proxy: {
      '/api/news': 'http://localhost:8891',
      '/api': 'http://localhost:8890',
      '/ws': {
        target: 'ws://localhost:8890',
        ws: true
      }
    }
  }
});
