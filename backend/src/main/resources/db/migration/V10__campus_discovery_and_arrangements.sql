CREATE TABLE campus_profile (
    shop_id bigint PRIMARY KEY REFERENCES shop(id),
    space_type varchar(16) NOT NULL DEFAULT 'LIBRARY' CHECK (space_type IN ('LIBRARY', 'STUDY', 'DISCUSSION')),
    category varchar(16) NOT NULL DEFAULT 'OTHER' CHECK (category IN ('NAILS', 'PRINT', 'PHOTO', 'CONSULT', 'CLUB', 'OTHER')),
    quiet_level varchar(16) NOT NULL DEFAULT 'UNSPECIFIED' CHECK (quiet_level IN ('QUIET', 'SOFT', 'DISCUSSION', 'UNSPECIFIED')),
    address varchar(180) NOT NULL DEFAULT '',
    rules varchar(500) NOT NULL DEFAULT ''
);
ALTER TABLE booking ADD COLUMN reader_id bigint REFERENCES reader_account(id);
CREATE INDEX booking_reader_schedule ON booking(reader_id, starts_at);
CREATE TABLE service_review (
    booking_id uuid PRIMARY KEY REFERENCES booking(id),
    rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body varchar(500) NOT NULL,
    created_at timestamptz NOT NULL
);
