-- Surveys context: event-sourced plot ownership. There is no mutable "current holder" row — a survey
-- plot's state is the fold of this append-only event stream, and its full provenance (who held it
-- when) is the stream itself.
--
-- `sequence` is the plot's stream position (0-based). The (plot_id, sequence) primary key is the
-- optimistic-concurrency guard: a command appends at the version it replayed, so two concurrent
-- writers racing for the same position collide on the key and exactly one wins. Events are stored as
-- JSONB and read back by `event_type`, the same idiom the shared outbox uses.

CREATE SCHEMA IF NOT EXISTS surveys;

CREATE TABLE surveys.survey_plot_events (
    plot_id     UUID        NOT NULL,
    sequence    INTEGER     NOT NULL CHECK (sequence >= 0),
    event_type  VARCHAR(64) NOT NULL,
    payload     JSONB       NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (plot_id, sequence)
);
