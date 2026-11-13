CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE booking (
    id uuid PRIMARY KEY,
    shop_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    service_id bigint NOT NULL,
    service_name varchar(80) NOT NULL,
    employee_name varchar(60) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL CHECK (ends_at > starts_at),
    customer_name varchar(60) NOT NULL,
    contact varchar(80) NOT NULL,
    status varchar(10) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    request_key uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (shop_id, employee_id) REFERENCES employee(shop_id, id),
    FOREIGN KEY (shop_id, service_id) REFERENCES service(shop_id, id),
    UNIQUE (shop_id, request_key),
    CONSTRAINT booking_no_overlap EXCLUDE USING gist (
        employee_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED')
);

CREATE INDEX booking_shop_time ON booking(shop_id, starts_at);
CREATE INDEX booking_employee_time ON booking(employee_id, starts_at);
