import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          animation: ['gsap', '@gsap/react'],
          react: ['react', 'react-dom']
        }
      }
    }
  },
  server: {
    port: 8880,
    proxy: {
      '/api': 'http://localhost:8890',
      '/ws': {
        target: 'ws://localhost:8890',
        ws: true
      }
    }
  }
});
