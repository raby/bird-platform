-- eBird integration: an outbound sync of recorded sightings to eBird, driven by the SightingCreated
-- outbox event. Each sighting becomes a submission row (PENDING), which a scheduled dispatcher pushes
-- to eBird through an anti-corruption layer, wrapped in a circuit breaker. sighting_id is the primary
-- key, so a re-delivered event never queues a second submission (idempotent consume).

CREATE SCHEMA IF NOT EXISTS ebird;

CREATE TABLE ebird.submission (
    sighting_id    UUID             PRIMARY KEY,   -- the source sighting; the idempotency key
    species_id     TEXT             NOT NULL,
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    observed_at    TIMESTAMPTZ      NOT NULL,
    observed_count INTEGER          NOT NULL,
    status         TEXT             NOT NULL CHECK (status IN ('PENDING', 'SUBMITTED', 'FAILED')),
    attempts       INTEGER          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ      NOT NULL,
    updated_at     TIMESTAMPTZ      NOT NULL,
    submitted_at   TIMESTAMPTZ
);

-- Keeps the dispatcher's claim of undelivered submissions fast as submitted rows accumulate.
CREATE INDEX idx_ebird_submission_pending ON ebird.submission (created_at) WHERE status = 'PENDING';
