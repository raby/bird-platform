-- Permits context: a per-hide daily visitor cap, debited by the visitor-permit saga when a booking is
-- confirmed. A landowner caps how many visitors a hide may take in a day during a rare-bird surge;
-- when the cap is hit, the saga compensates by cancelling the over-cap booking (see VisitorPermitSaga
-- in the api module). The booking's own slot exclusivity (V303) is a separate, per-slot concern.

CREATE SCHEMA IF NOT EXISTS permits;

-- The configured cap and running count for a (hide, day). No row means no cap is configured for that
-- hide and day, so the saga issues freely. `issued` is debited by the saga's atomic conditional
-- UPDATE (issued + party_size <= capacity), so the count can never exceed the cap even under retries.
CREATE TABLE permits.visitor_permits (
    hide_id   UUID    NOT NULL,
    day       DATE    NOT NULL,
    capacity  INTEGER NOT NULL CHECK (capacity > 0),
    issued    INTEGER NOT NULL DEFAULT 0 CHECK (issued >= 0),
    PRIMARY KEY (hide_id, day),
    CONSTRAINT visitor_permits_within_cap CHECK (issued <= capacity)
);

-- Idempotency ledger: one row per booking that has been granted a permit, so the at-least-once outbox
-- relay re-delivering BookingConfirmed cannot debit the cap twice (the saga checks this first).
CREATE TABLE permits.permit_issuance (
    booking_id  UUID        PRIMARY KEY,
    hide_id     UUID        NOT NULL,
    day         DATE        NOT NULL,
    party_size  INTEGER     NOT NULL CHECK (party_size > 0),
    issued_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_permit_issuance_hide_day ON permits.permit_issuance (hide_id, day);

-- Seed a low daily cap (4 visitors) on two demo hides for a two-week window from first boot, so the
-- saga is demonstrable: book enough visitors on Kingfisher or Osprey for one day and the cap trips,
-- auto-cancelling the booking that tips it over. The other demo hides stay uncapped.
INSERT INTO permits.visitor_permits (hide_id, day, capacity, issued)
SELECT h.hide_id, CURRENT_DATE + g, 4, 0
FROM (VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid),  -- Kingfisher Hide
    ('10000000-0000-0000-0000-000000000002'::uuid)   -- Osprey Platform
) AS h(hide_id),
     generate_series(0, 14) AS g;
