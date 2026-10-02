-- Slot exclusivity as a storage-layer guarantee, not just a service-layer check.
--
-- BookingService.request() calls hasOverlappingActiveBooking() before inserting, but that check and
-- the insert are not atomic: two concurrent requests for the same hide and an overlapping slot can
-- both pass the check before either commits (a classic check-then-act / TOCTOU race). This exclusion
-- constraint closes the gap in the database itself — an overlapping active booking on a hide is now
-- physically impossible. Under the race the loser's INSERT blocks on the GiST predicate lock and then
-- fails with 23P01; JdbcBookingRepository translates that into the same HideSlotUnavailable the
-- pre-check returns, so the service-layer check becomes a fast path and the constraint the authority.
--
-- btree_gist supplies the equality operator class so a single GiST index can combine `hide_id WITH =`
-- and the slot range `WITH &&` (overlap). The range is half-open [start, end) to match the overlap
-- test (slot_start < end AND slot_end > start): two bookings that merely touch at an endpoint do not
-- conflict. The partial WHERE excludes cancelled bookings, so cancelling frees the slot — exactly as
-- hasOverlappingActiveBooking already treats status <> 'CANCELLED'.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings.bookings
    ADD CONSTRAINT bookings_no_overlapping_active_slot
    EXCLUDE USING gist (
        hide_id WITH =,
        tstzrange(slot_start, slot_end, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED');
