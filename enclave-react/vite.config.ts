import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('lottie-web') || id.includes('lottie-react')) return 'vendor-lottie';
            return 'vendor';
          }
        },
      },
    },
  },
});