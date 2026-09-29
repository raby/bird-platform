import { defineConfig } from 'vitest/config'

// The dev server proxies /api → the Spring backend, so the SPA can talk to the real REST
// controllers cross-origin-free in dev. With no backend up, the app falls back to an in-memory
// matcher (see src/api.ts) so it still runs standalone. (Wired for real at slice U5.)
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
  },
})
