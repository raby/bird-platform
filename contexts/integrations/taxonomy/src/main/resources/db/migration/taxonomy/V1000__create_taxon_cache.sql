-- Taxonomy context: a read-through cache over the external taxonomy authority. Taxonomy is near-static
-- reference data behind a rate-limited API, so lookups are cached by scientific name with the time they
-- were fetched; the service decides freshness against a TTL and falls back to a stale row when the
-- authority is unreachable. The cache starts empty and fills on lookups.

CREATE SCHEMA IF NOT EXISTS taxonomy;

CREATE TABLE taxonomy.taxon_cache (
    scientific_name     TEXT        PRIMARY KEY,
    common_name         TEXT        NOT NULL,
    family              TEXT        NOT NULL,
    taxon_order         TEXT        NOT NULL,
    conservation_status TEXT        NOT NULL,
    fetched_at          TIMESTAMPTZ NOT NULL
);
