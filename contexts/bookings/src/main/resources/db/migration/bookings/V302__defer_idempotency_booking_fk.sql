-- BookingService.request() claims the idempotency key (the atomic first-writer gate) BEFORE it
-- inserts the booking row that key references: a concurrent duplicate request then short-circuits on
-- the key's unique constraint and returns the winner's booking, without ever inserting an orphan
-- booking that would hold the slot forever. That ordering needs the foreign key validated at COMMIT —
-- by when the booking has been inserted in the same transaction — not eagerly at INSERT time. Making
-- the constraint DEFERRABLE INITIALLY DEFERRED does exactly that; the unique constraint on `key` still
-- fires immediately, so the mutual-exclusion behaviour under a race is unchanged.
ALTER TABLE bookings.idempotency_keys
    ALTER CONSTRAINT idempotency_keys_booking_id_fkey DEFERRABLE INITIALLY DEFERRED;
