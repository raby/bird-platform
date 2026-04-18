# Domain Context — DigitalBluebird Birding Platform

A working brief for interviews, onboarding, and design discussions. Read this first — everything else assumes the vocabulary here.

---

## 1. What we're building

**DigitalBluebird** is a citizen-science platform for bird observers. It lets people:

- **Log sightings** — what they saw, where, when — and browse a searchable global species catalogue.
- **Book nature-reserve bird hides** and **guided expeditions** (dawn chorus walks, pelagic seabird trips, ringing demos).
- **Claim breeding-bird survey plots** for a season and contribute structured monitoring data.
- **Request access permits** during rare-bird events, where landowners cap daily visitors.
- **Share sightings outward** via the eBird / iNaturalist APIs.

Behind the scenes it is a deliberate "greatest-hits" tour of backend patterns — hexagonal architecture, CQRS, sagas, outbox, event sourcing, optimistic locking, TTL inventory, anti-corruption layers — each applied **where the domain actually warrants it**, not as pattern cosplay.

---

## 2. Why this domain

Ornithology is an unusually rich bounded-context playground — every technical choice has a plain domain reason behind it, which makes it easy to tell a coherent story in an interview.

| Real-world need                            | Technical analogue                                  |
|--------------------------------------------|-----------------------------------------------------|
| Species taxonomy & fuzzy search            | Read-optimised NoSQL (Elasticsearch / Mongo)        |
| Legally-binding sighting records           | Transactional, audit-friendly SQL (Postgres)        |
| Limited hide capacity on migration dawns   | Concurrency control, seat-reservation patterns      |
| Survey plot ownership across a season      | Exclusive resource claim + event-sourced provenance |
| Mega-rarity surge traffic                  | Rate limiting, TTL inventory, queue load-levelling  |
| eBird / iNaturalist sync                   | Anti-corruption layers, outbox-driven integration   |

---

## 3. Birding glossary

*For the non-birder interviewer or onboarding engineer — what the jargon means.*

- **Sighting / Observation** — a record that a given species was seen at a given place and time.
- **Species / Taxon** — the identifier of a bird kind. Taxonomy is a nested tree: order → family → genus → species.
- **Lifer** — the first time someone has ever seen a given species. A personal milestone.
- **Twitch / Twitcher** — a focused trip to see a specific rare bird. The twitcher is the person.
- **Mega-rarity** — a bird so far off its normal range that hundreds of birders will travel to see it. A surge-traffic event in our system.
- **Hide (UK) / Blind (US)** — a wooden or camouflaged shelter at a reserve with slit windows; capacity is limited (often 6–12 people).
- **Reserve** — a managed nature site (RSPB, WWT, National Trust, etc.) that hosts hides and runs events.
- **Ringing / Banding** — fitting birds with numbered leg rings under licence, to track movement and longevity.
- **Ringer** — a licensed bird-ringer. Different licence tiers (C permit → A permit) dictate what they can do solo.
- **Pelagic** — a boat trip offshore to see seabirds. Weather-dependent, high no-show risk.
- **BBS (Breeding Bird Survey)** — a structured scheme where volunteers census a fixed 1 km² plot twice each spring.
- **Tetrad / Plot** — a defined geographic square (often 1 km² or 2 km²) used as a survey unit.
- **County list / Year list / Life list** — personal totals of species seen in a region or lifetime. Core gamification.

---

## 4. Technical glossary

*For interviewers allergic to buzzwords — what each term concretely means in this codebase.*

- **Hexagonal architecture (ports & adapters)** — domain code knows nothing about HTTP, SQL, or frameworks. It defines **ports** (interfaces); adapters (REST, JDBC, Kafka) plug in from outside. Keeps the domain testable with plain JVM tests.
- **CQRS-lite** — the write model (Postgres, normalised) is separate from the read model (Mongo/ES, denormalised). We are not splitting services; just stores.
- **Saga** — a long-running business process broken into local transactions, each with a compensating action. Used when you cannot wrap the whole thing in a single DB transaction (e.g. booking + payment + email).
- **Outbox pattern** — domain writes and an "event emitted" row land in the **same** DB transaction; a relay publishes the event later. Guarantees at-least-once delivery without two-phase commit.
- **Event sourcing** — store the sequence of *events* (what happened) rather than current state; derive state by replaying. We use it only where event history is a first-class product feature (survey plots).
- **Optimistic locking** — a `@Version` column plus "if version matches, update; else retry". Preferred when contention is rare.
- **Anti-corruption layer (ACL)** — a translation wrapper around an external system so its model, naming, and quirks don't leak into your domain.
- **Read projection** — a denormalised materialised view of the write store, built from events, optimised for a specific query shape.

---

## 5. Bounded contexts — what & why

Each context is a module. Each has **one reason to change** and **one team-sized chunk of responsibility**.

### 5.1 Identity

**What** — accounts, roles (observer / ringer / reserve-admin / moderator), auth tokens.
**Why its own context** — every other context needs "who is the actor?", but identity's lifecycle (sign-up, password reset, role grants) is independent of sightings, bookings, etc.
**Interview angle** — the reason most real systems have an identity service: avoiding accidental coupling between "who you are" and "what you did".

### 5.2 Observations (sightings, write side)

**What** — a user records that they saw *species X* at *location Y* at *time T*, possibly with a photo.
**Why** — core domain. Audit-heavy: a misidentified rarity needs a correction trail, not silent mutation.
**Patterns exercised** — hexagonal domain model, aggregate roots with invariants, outbox publishing `SightingCreated` events.
**Interview angle** — *"This is where the domain model earns its keep — a `Sighting` isn't a DTO, it has invariants (date can't be in the future, location must be land for a land bird, photo must be attributable to the observer)."*

### 5.3 Species catalogue (read side)

**What** — searchable taxonomy: scientific name, common names, range maps, images, conservation status.
**Why its own context** — read-heavy, search-heavy, rarely-written. The wrong tool to jam into Postgres.
**Data store** — Elasticsearch (fuzzy match, partial autocomplete, geo range queries).
**Patterns** — read projection fed from taxonomy imports; ACL wraps the Avibase / Clements taxonomy feeds.
**Interview angle** — *"Why two databases? Because search and transactions are different shapes. I'm not dogmatic about polyglot persistence, but fuzzy species lookup at human-typing latency is what ES is literally built for."*

### 5.4 Reserves & Hides (reference data)

**What** — the catalogue of reserves, the hides inside them, their capacity, opening hours, accessibility info.
**Why** — changes slowly, read by the Bookings context constantly. A classic reference-data module.
**Interview angle** — illustrates the difference between **reference data** (slowly-changing shared facts) and **transactional data** (sightings, bookings). Keeping them apart stops reserve admins from accidentally breaking booking invariants.

### 5.5 Bookings (hide slots)

**What** — a user reserves a seat in hide *H* for time slot *T*. Capacity is limited. Payment may apply. Lifecycle: 15-minute hold → confirmation → check-in → complete.
**Why it matters** — the **textbook concurrency story**. Dawn migration weekends generate real contention — three people clicking "book" on the last seat in the Minsmere Island Hide at 04:55.
**Patterns exercised**:
- **Optimistic locking** on slot capacity (`@Version`), with retry on conflict.
- **State machine**: `PENDING → CONFIRMED → CHECKED_IN → COMPLETED` (+ `CANCELLED`, `NO_SHOW`).
- **Saga** coordinating booking + payment + email, with compensations for refund-on-failure.
- **Idempotency keys** on payment requests (Stripe-style).
- **Scheduled job** to reap expired holds.

**Interview angle** — *"If an interviewer says 'tell me about a concurrency problem', this is the hook. I can talk optimistic vs pessimistic, retry strategy, back-off, how the saga compensates when the payment gateway times out."*

### 5.6 Expeditions (guided tours)

**What** — dawn chorus walks, pelagic trips, ringing demos. Multi-participant, scheduled, weather-cancellable.
**Why separate from Bookings** — different invariants: per-head pricing, weather cancellations trigger bulk refunds, reminders are time-sensitive.
**Patterns** — extended booking saga (per-participant payment), scheduled notifications via outbox → notifications context.
**Interview angle** — shows bounded-context discipline: *"These look similar to hide bookings, but the rules differ enough that merging them would be a mess in six months — and my module structure makes the difference explicit."*

### 5.7 Surveys (plot claims)

**What** — a volunteer claims a 1 km² plot for the breeding season. They alone may submit data for it. They can transfer it to another volunteer mid-season. Its history matters forever.
**Why** — **citizen-science data is only as trustworthy as its provenance**. Who held this plot in 2024? Who handed it over? When? This is where event sourcing earns its place.
**Patterns**:
- **Event sourcing** (`PlotClaimed`, `PlotTransferProposed`, `PlotTransferAccepted`, `PlotReleased`, `DataSubmitted`).
- **Transfer saga** — two-party handshake.
- **Read projections** into Mongo for "my plots", seasonal heatmaps, volunteer leaderboards.

**Interview angle** — *"I used event sourcing exactly once, on purpose. I can explain why it's right here and wrong for bookings — event sourcing is a tool for domains where the history is the product."*

### 5.8 Permits (rarity access windows)

**What** — when a mega-rarity is reported on private land, the landowner opens a permit window (e.g. "50 visitors per day for 5 days"). Requests surge. Permits issue quickly and expire.
**Why its own context** — surge traffic + short-lived inventory have a totally different runtime profile to hide bookings.
**Patterns**:
- **TTL inventory in Redis** (atomic `DECR`, expiry on the window).
- **Token-bucket rate limiting** at the API edge.
- **Queue-based load-levelling** — requests land in a queue; a worker drains under cap.
- **Event-driven entry** — `RarityReported` triggers `PermitWindowOpened`.

**Interview angle** — *"Here's where NoSQL pays rent. Redis isn't just 'a cache' — it's the primary store for permit inventory because of atomic ops and native TTL. Use the right tool."*

### 5.9 Notifications

**What** — outbound emails, push notifications, reminders.
**Why** — every other context emits events; notifications consume them. Keeps domain logic free of SMTP concerns.
**Pattern** — outbox consumer, idempotent delivery, dead-letter queue for permanent failures.
**Interview angle** — *"Proves the outbox isn't just a diagram — it's the only way notifications get sent, so if it breaks, we notice."*

### 5.10 Integrations (eBird, iNaturalist, Avibase)

**What** — sync sightings outbound to eBird; ingest species taxonomy from Avibase.
**Why separate** — external APIs are chaotic. Their model must never leak into ours.
**Pattern** — **anti-corruption layer** + retry/circuit-breaker + idempotency per external record.
**Interview angle** — *"If eBird renames a field, exactly one translator class breaks. The domain doesn't notice."*

---

## 6. Interview cheat-sheet

Canned answers — memorise the shape, not the wording.

### "Tell me about this project"
> A citizen-science platform for birders. I built it specifically to exercise a range of backend patterns — each one chosen because the domain genuinely needs it, not for its own sake. Hexagonal core, Postgres for transactions, Elasticsearch for species search, Redis for short-lived inventory, an outbox between contexts.

### "Why two databases?"
> Different data shapes. Sightings are structured, audited, and transactional — that's Postgres's job. Species search needs fuzzy match and autocomplete at typing latency — that's Elasticsearch's job. Jamming either workload into the wrong store would be painful.

### "Walk me through a booking."
> A POST hits a REST adapter, which calls a domain port. The domain service starts a saga: reserve capacity with optimistic lock, create a pending booking, publish `BookingPending` via the outbox, call the payment gateway with an idempotency key. On success, transition to `CONFIRMED` and emit `BookingConfirmed`. On payment failure, the compensating step releases capacity and emits `BookingCancelled`. The Notifications context picks up both events and emails accordingly.

### "Why event sourcing for plots and not bookings?"
> Because survey-plot history *is* the product. A scientist three years from now needs to know exactly who held that plot and when — that's provenance, not audit. Bookings don't have that requirement — nobody cares who held seat 4 last Tuesday — so event sourcing would just be overhead.

### "How do you prevent double-booking?"
> Optimistic locking on the slot's capacity counter with `@Version`. On version conflict I retry with a short back-off. Contention is rare — even on migration dawns — so I'm not paying pessimistic-lock cost 99% of the time. If this ever became a hot spot I'd swap to a Redis atomic decrement, which is exactly what the Permits context already uses.

### "What happens if the payment gateway times out?"
> The saga's compensating action releases the capacity hold and transitions the booking to `CANCELLED`. The idempotency key means a retry of the same payment call is safe — we won't double-charge.

### "What would you add next?"
> A moderation workflow for disputed rare-bird records. It would live as its own context, consume `SightingCreated` events, and expose its own review state machine. The existing contexts don't block it — that's the payoff of clean boundaries.

---

## 7. Scope note

This is a portfolio project, not a production citizen-science platform. The intent is **depth in a few vertical slices**, not breadth across every feature. A realistic build order:

1. **Observations + Species** — the domain spine, proves hexagonal + CQRS.
2. **Bookings** — the concurrency showpiece.
3. **Permits** — the Redis/surge showpiece.
4. **Surveys** — the event-sourcing showpiece.
5. **Expeditions, Notifications, Integrations** — round it out if time allows.

Each slice is interview-demonstrable on its own.
