CREATE SCHEMA IF NOT EXISTS bookings;

CREATE TABLE bookings.bookings (
    id                UUID PRIMARY KEY,
    hide_id           UUID        NOT NULL,
    observer_id       UUID        NOT NULL,
    slot_start        TIMESTAMPTZ NOT NULL,
    slot_end          TIMESTAMPTZ NOT NULL,
    party_size        INTEGER     NOT NULL CHECK (party_size BETWEEN 1 AND 20),
    status            VARCHAR(16) NOT NULL CHECK (status IN ('REQUESTED', 'CONFIRMED', 'CANCELLED')),
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT slot_range_valid CHECK (slot_start < slot_end)
);

CREATE INDEX idx_bookings_hide_slot
    ON bookings.bookings (hide_id, slot_start, slot_end)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_bookings_observer_time
    ON bookings.bookings (observer_id, slot_start DESC);

CREATE TABLE bookings.idempotency_keys (
    key         VARCHAR(128) PRIMARY KEY,
    booking_id  UUID        NOT NULL REFERENCES bookings.bookings (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
