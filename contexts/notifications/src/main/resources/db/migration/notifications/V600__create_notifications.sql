-- Notifications context: an idempotent consumer of the platform's domain events (the "inbox" pattern).
-- Booking lifecycle events arrive over the at-least-once outbox relay, so the same event may be
-- delivered more than once; enqueuing is deduplicated on the source event id, so one event yields at
-- most one notification. A separate dispatcher then delivers pending notifications at-least-once.

CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE notifications.notification (
    id              UUID        PRIMARY KEY,
    kind            TEXT        NOT NULL,
    recipient       TEXT        NOT NULL,
    subject         TEXT        NOT NULL,
    body            TEXT        NOT NULL,
    -- The idempotency (inbox) key: the id of the event that produced this notification. UNIQUE makes a
    -- re-delivered event a no-op (ON CONFLICT DO NOTHING on insert), so processing is effectively-once.
    source_event_id UUID        NOT NULL UNIQUE,
    status          TEXT        NOT NULL CHECK (status IN ('PENDING', 'SENT')),
    created_at      TIMESTAMPTZ NOT NULL,
    sent_at         TIMESTAMPTZ
);

-- Keeps the dispatcher's claim of undelivered notifications fast as sent rows accumulate.
CREATE INDEX idx_notification_pending ON notifications.notification (created_at) WHERE status = 'PENDING';
