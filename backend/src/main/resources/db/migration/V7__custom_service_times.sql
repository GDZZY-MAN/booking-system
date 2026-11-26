-- Replace the original duration/shift checks, keeping their range and day constraints.
DO $$
DECLARE item record;
BEGIN
    FOR item IN SELECT conname, conrelid::regclass AS relation
        FROM pg_constraint WHERE contype = 'c'
        AND conrelid IN ('service'::regclass, 'weekly_shift'::regclass)
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', item.relation, item.conname);
    END LOOP;
END $$;

ALTER TABLE service ADD CONSTRAINT service_duration_minutes_check CHECK (duration_minutes BETWEEN 1 AND 480);
ALTER TABLE weekly_shift ADD CONSTRAINT weekly_shift_weekday_check CHECK (weekday BETWEEN 1 AND 7);
ALTER TABLE weekly_shift ADD CONSTRAINT weekly_shift_start_minute_check CHECK (start_minute >= 0 AND start_minute < 1440);
ALTER TABLE weekly_shift ADD CONSTRAINT weekly_shift_end_minute_check CHECK (end_minute <= 1440 AND end_minute > start_minute);
