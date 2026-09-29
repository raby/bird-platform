# bird-platform UI

A fine-grained single-page UI for the bird-platform REST API, **hand-built on
[`@raby/ripple`](../../ripple)** — no framework, no VDOM. This is ripple's flagship real-world
consumer: the app is rendered *by* the signals library, which is the most credible demo there is
(and the reason ripple is dogfooded here before its `0.1.0` release).

## Why no framework

The point is to prove the fine-grained reactive model end to end. State is signals, derived UI text
is `computed`, and every DOM update is an `effect` — including the async, abortable species search,
whose debounce rides on ripple's effect-cleanup contract. The whole app (ripple + the DOM binding +
the feature) ships in **~3.3 kB gzipped**.

## Layout

| File | Role |
|---|---|
| `src/dom.ts` | The reactive DOM binding — `text` / `attr` / `classes` / `on` / `list` / `show` + a `scope` collector. Each helper is just an `effect`. Candidate for extraction to `@raby/ripple-dom` later. |
| `src/api.ts` | Typed client for the REST API; types mirror the Kotlin DTOs exactly. |
| `src/mock.ts` | In-memory matcher over the backend's seed data, used when no backend is configured. |
| `src/search.ts` | The species-search feature: signals + the binding, wired to `GET /species/search`. |

## Run

```sh
npm install        # links ../../ripple via a file: dependency (unpublished during dogfooding)
npm run dev        # http://localhost:5173 — runs standalone against the in-memory matcher
npm run test       # vitest + jsdom: proves ripple drives the DOM (incl. debounce/abort)
npm run build      # tsc --noEmit && vite build
```

By default the app uses the in-memory matcher (same seed data and ≥2-character rule as the backend),
so it runs with no backend. To hit the real controllers, set `VITE_API_BASE` (e.g. `/api`, proxied
to `http://localhost:8080` by the dev server).

> Once ripple is published, the `file:../../ripple` dependency becomes `@raby/ripple@^0.1.0`.

## Status

- **U2 — species search:** done (this slice).
- **U3/U4 — booking flow + the optimistic-lock race demo:** next (need the U1 hide-availability read
  projection on the backend first).

See the design of record: `docs/SIGNALS_UI_DESIGN.md` in the portfolio site repo.
