-- Keep reservation history while removing check-in and its penalties.
ALTER TABLE seat_reservation DROP CONSTRAINT seat_no_overlap;
DROP INDEX reader_one_active_seat;
ALTER TABLE seat_reservation DROP CONSTRAINT seat_reservation_status_check;

UPDATE seat_reservation SET status = 'CANCELLED' WHERE status = 'NO_SHOW';
UPDATE seat_reservation SET status = 'ACTIVE' WHERE status = 'CHECKED_IN';
UPDATE seat_reservation SET status = 'COMPLETED'
    WHERE status IN ('RESERVED', 'ACTIVE') AND ends_at <= CURRENT_TIMESTAMP;
UPDATE seat_reservation SET status = 'ACTIVE'
    WHERE status = 'RESERVED' AND starts_at <= CURRENT_TIMESTAMP AND ends_at > CURRENT_TIMESTAMP;
UPDATE reader_account SET no_show_streak = 0, blocked_until = NULL;

ALTER TABLE seat_reservation ADD CONSTRAINT seat_reservation_status_check
    CHECK (status IN ('RESERVED', 'ACTIVE', 'COMPLETED', 'CANCELLED'));
ALTER TABLE seat_reservation ADD CONSTRAINT seat_no_overlap EXCLUDE USING gist (
    seat_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status IN ('RESERVED', 'ACTIVE'));
CREATE UNIQUE INDEX reader_one_active_seat ON seat_reservation(reader_id)
    WHERE status IN ('RESERVED', 'ACTIVE');
