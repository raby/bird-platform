# bird-platform UI

A fine-grained single-page UI for the bird-platform REST API, **hand-built on
[`@raby/ripple`](../../ripple)** — no framework, no VDOM. This is ripple's flagship real-world
consumer: the app is rendered *by* the signals library, which is the most credible demo there is
(and the reason ripple is dogfooded here before its `0.1.0` release).

## Why no framework

The point is to prove the fine-grained reactive model end to end. State is signals, derived UI text
is `computed`, and every DOM update is an `effect` — including the async, abortable species search,
whose debounce rides on ripple's effect-cleanup contract. The whole app — ripple, the DOM binding and
all three features (booking flow, version-race, species search) — ships in **~6.6 kB of gzipped
JavaScript** (plus ~1.7 kB CSS).

## Layout

| File | Role |
|---|---|
| `src/dom.ts` | The reactive DOM binding — `text` / `attr` / `classes` / `on` / `list` / `show` + a `scope` collector. Each helper is just an `effect`. Candidate for extraction to `@raby/ripple-dom` later. |
| `src/api.ts` | Typed client for the REST API; types mirror the Kotlin DTOs exactly. Switches on `VITE_API_BASE` (unset → the mock). |
| `src/mock.ts` | In-memory matcher + booking store over the backend's seed data, used when no backend is configured. Mirrors the concurrency semantics (CAS, idempotency, state machine) faithfully. |
| `src/app.ts` | App shell: the three views, tab state as a ripple signal. |
| `src/search.ts` | Species search: signals + the binding, wired to `GET /species/search`. |
| `src/booking.ts` | Booking flow: create → confirm/cancel, carrying the optimistic-lock version. |
| `src/race.ts` | Version-race demo: two panels holding stale versions, one wins the CAS and one gets a 409. |
| `src/util.ts` | Shared `el` / formatting helpers. |

## Run

```sh
npm install        # pulls @raby/ripple@^0.1.0 from npm
npm run dev        # http://localhost:5173 — runs standalone against the in-memory mock
npm run test       # vitest + jsdom: proves ripple drives the DOM (incl. debounce/abort + the race)
npm run build      # tsc --noEmit && vite build
```

The app picks its data source from `VITE_API_BASE` (see `src/api.ts`):

- **unset** → the in-memory mock (same seed data, ≥2-character rule and concurrency semantics as the
  backend), so it runs with no backend at all;
- **`/api`** → the real controllers: in dev the server proxies `/api` → `http://localhost:8080`; in
  the demo container Spring serves both the SPA and the API from the same origin.

## Live demo (the whole platform in one container)

The backend repo ships a `demo` profile + `docker-compose.demo.yml` that bakes this SPA (built with
`VITE_API_BASE=/api`) into the Spring Boot jar and runs it on Postgres alone — no Elasticsearch:

```sh
cd ../..                                              # bird-platform repo root
docker compose -f docker-compose.demo.yml up --build  # → http://localhost:8080
```

## Status

- **U2–U4 — species search, booking flow, version-race demo:** done.
- **U5 — demo profile + container:** done (this slice).

See the design of record: `docs/SIGNALS_UI_DESIGN.md` in the portfolio site repo.
