import { defineConfig } from 'vitest/config'

// Three ways to run the SPA, all off one src/api.ts switch on VITE_API_BASE:
//   • Standalone (no VITE_API_BASE): falls back to the in-memory mock (src/mock.ts) — runs anywhere.
//   • Dev against a local backend (VITE_API_BASE=/api + `gradlew :api:bootRun`): the dev server below
//     proxies /api → the backend (default profile serves controllers at the root, so /api is stripped).
//   • The demo container (docker-compose.demo.yml): built with VITE_API_BASE=/api and served by Spring
//     itself under the `demo` profile, which mounts controllers under /api — same origin, no proxy.
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
