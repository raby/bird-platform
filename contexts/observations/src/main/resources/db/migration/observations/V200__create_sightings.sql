CREATE SCHEMA IF NOT EXISTS observations;

CREATE TABLE observations.sightings (
    id           UUID PRIMARY KEY,
    observer_id  UUID             NOT NULL,
    species_id   VARCHAR(64)      NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    observed_at  TIMESTAMPTZ      NOT NULL,
    count        INTEGER          NOT NULL CHECK (count > 0),
    notes        TEXT,
    created_at   TIMESTAMPTZ      NOT NULL
);

CREATE INDEX idx_sightings_observer_time
    ON observations.sightings (observer_id, observed_at DESC);

CREATE INDEX idx_sightings_species_time
    ON observations.sightings (species_id, observed_at DESC);
