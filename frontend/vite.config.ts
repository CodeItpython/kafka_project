import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 8880,
    proxy: {
      '/api/news': 'http://localhost:8891',
      // shopping-service owns the Naver shopping catalog + cart.
      '/api/shopping': 'http://localhost:8893',
      // chat-service owns chat, notifications and the DLT admin endpoints.
      '/api/chat': 'http://localhost:8892',
      '/api/notifications': 'http://localhost:8892',
      '/api/admin/kafka': 'http://localhost:8892',
      // order-service owns checkout, orders and the mock payment.
      '/api/orders': 'http://localhost:8895',
      '/api': 'http://localhost:8890',
      // signaling-service owns the video-call WebRTC signaling socket. Must come
      // before '/ws' since '/ws-signal' also has the '/ws' prefix.
      '/ws-signal': {
        target: 'ws://localhost:8894',
        ws: true
      },
      '/ws': {
        target: 'ws://localhost:8892',
        ws: true
      }
    }
  }
});
