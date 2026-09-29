-- Hide-availability read model (CQRS). Capacity is seeded reference data (there is no Hide
-- aggregate yet; the reserves context is an empty scaffold); occupancy is projected from
-- confirmed-booking events by HideAvailabilityProjector, kept separate from the write model.

CREATE TABLE bookings.hides (
    id        UUID PRIMARY KEY,
    name      VARCHAR(128) NOT NULL,
    reserve   VARCHAR(128) NOT NULL,
    capacity  INTEGER      NOT NULL CHECK (capacity BETWEEN 1 AND 100)
);

CREATE TABLE bookings.hide_availability (
    booking_id   UUID PRIMARY KEY,
    hide_id      UUID        NOT NULL,
    observer_id  UUID        NOT NULL,
    slot_start   TIMESTAMPTZ NOT NULL,
    slot_end     TIMESTAMPTZ NOT NULL,
    party_size   INTEGER     NOT NULL CHECK (party_size BETWEEN 1 AND 20),
    confirmed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT hide_availability_slot_valid CHECK (slot_start < slot_end)
);

CREATE INDEX idx_hide_availability_hide_slot
    ON bookings.hide_availability (hide_id, slot_start, slot_end);

-- Seed demo hides so the read model has capacity to report against.
INSERT INTO bookings.hides (id, name, reserve, capacity) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Kingfisher Hide', 'Leighton Moss', 6),
    ('10000000-0000-0000-0000-000000000002', 'Osprey Platform', 'Loch Garten',   4),
    ('10000000-0000-0000-0000-000000000003', 'Marsh Hide',      'Ham Wall',      8),
    ('10000000-0000-0000-0000-000000000004', 'Bittern Screen',  'Minsmere',      5);
