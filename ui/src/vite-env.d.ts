/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the bird-platform REST API. Unset → the app uses the in-memory matcher
   *  (src/mock.ts) so it runs standalone; set to e.g. `/api` (proxied) to hit the real backend. */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
