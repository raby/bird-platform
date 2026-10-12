-- Expeditions context: a process manager that books several hides for one party as a single
-- all-or-nothing outing. The expedition row is the persisted state machine (BOOKING → CONFIRMED, or
-- COMPENSATING → FAILED); each expedition_leg is one hide and the booking that holds it. If any leg
-- cannot be held — a slot clash at plan time, or the visitor-permit saga cancelling an over-cap booking
-- later — the already-booked legs are released (CANCELLED) and the expedition fails.

CREATE SCHEMA IF NOT EXISTS expeditions;

CREATE TABLE expeditions.expedition (
    id         UUID        PRIMARY KEY,
    leader_id  TEXT        NOT NULL,
    party_size INTEGER     NOT NULL CHECK (party_size > 0),
    status     TEXT        NOT NULL CHECK (status IN ('BOOKING', 'CONFIRMED', 'COMPENSATING', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE expeditions.expedition_leg (
    expedition_id UUID        NOT NULL REFERENCES expeditions.expedition (id),
    seq           INTEGER     NOT NULL,
    hide_id       TEXT        NOT NULL,
    slot_start    TIMESTAMPTZ NOT NULL,
    slot_end      TIMESTAMPTZ NOT NULL,
    booking_id    UUID,
    status        TEXT        NOT NULL CHECK (status IN ('PENDING', 'BOOKED', 'CANCELLED', 'UNAVAILABLE')),
    PRIMARY KEY (expedition_id, seq)
);

-- Correlate a cancelled booking back to its expedition leg (the async compensation path). A booking
-- belongs to exactly one leg.
CREATE UNIQUE INDEX idx_expedition_leg_booking
    ON expeditions.expedition_leg (booking_id) WHERE booking_id IS NOT NULL;
