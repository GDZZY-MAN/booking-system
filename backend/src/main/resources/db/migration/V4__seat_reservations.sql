CREATE TABLE reader_account (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username varchar(80) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    verified_phone varchar(24) UNIQUE,
    no_show_streak integer NOT NULL DEFAULT 0 CHECK (no_show_streak >= 0),
    blocked_until timestamptz
);

CREATE TABLE library (
    shop_id bigint PRIMARY KEY REFERENCES shop(id),
    published boolean NOT NULL DEFAULT false,
    open_minute integer NOT NULL CHECK (open_minute >= 0 AND open_minute % 30 = 0),
    close_minute integer NOT NULL CHECK (close_minute <= 1440 AND close_minute > open_minute AND close_minute % 30 = 0),
    slot_minutes integer NOT NULL CHECK (slot_minutes BETWEEN 30 AND 240 AND slot_minutes % 30 = 0)
);

CREATE TABLE library_seat (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id bigint NOT NULL REFERENCES library(shop_id),
    label varchar(30) NOT NULL,
    area varchar(50) NOT NULL,
    near_window boolean NOT NULL DEFAULT false,
    power_outlet boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    UNIQUE (shop_id, label),
    UNIQUE (shop_id, id)
);

CREATE TABLE seat_reservation (
    id uuid PRIMARY KEY,
    shop_id bigint NOT NULL,
    seat_id bigint NOT NULL,
    reader_id bigint NOT NULL REFERENCES reader_account(id),
    seat_label varchar(30) NOT NULL,
    area varchar(50) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL CHECK (ends_at > starts_at),
    status varchar(16) NOT NULL CHECK (status IN ('RESERVED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    request_key uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (reader_id, request_key),
    FOREIGN KEY (shop_id, seat_id) REFERENCES library_seat(shop_id, id),
    CONSTRAINT seat_no_overlap EXCLUDE USING gist (
        seat_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('RESERVED', 'CHECKED_IN'))
);
CREATE UNIQUE INDEX reader_one_active_seat ON seat_reservation(reader_id)
    WHERE status IN ('RESERVED', 'CHECKED_IN');
CREATE INDEX seat_reservation_shop_time ON seat_reservation(shop_id, starts_at);
CREATE INDEX seat_reservation_expiry ON seat_reservation(status, starts_at, ends_at);
