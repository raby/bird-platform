CREATE SCHEMA IF NOT EXISTS shared_infra;

CREATE TABLE shared_infra.outbox (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(128) NOT NULL,
    aggregate_id    VARCHAR(128) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished
    ON shared_infra.outbox (occurred_at)
    WHERE published_at IS NULL;
