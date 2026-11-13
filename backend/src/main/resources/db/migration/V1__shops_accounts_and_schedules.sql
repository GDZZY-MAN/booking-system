CREATE TABLE shop (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug varchar(60) NOT NULL UNIQUE,
    name varchar(80) NOT NULL,
    description varchar(500) NOT NULL DEFAULT '',
    published boolean NOT NULL DEFAULT false
);

CREATE TABLE service (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id bigint NOT NULL REFERENCES shop(id),
    name varchar(80) NOT NULL,
    duration_minutes integer NOT NULL CHECK (duration_minutes BETWEEN 15 AND 480 AND duration_minutes % 15 = 0),
    active boolean NOT NULL DEFAULT true,
    UNIQUE (shop_id, id)
);

CREATE TABLE employee (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id bigint NOT NULL REFERENCES shop(id),
    name varchar(60) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    UNIQUE (shop_id, id)
);

CREATE TABLE employee_service (
    shop_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    service_id bigint NOT NULL,
    PRIMARY KEY (employee_id, service_id),
    FOREIGN KEY (shop_id, employee_id) REFERENCES employee(shop_id, id),
    FOREIGN KEY (shop_id, service_id) REFERENCES service(shop_id, id)
);

CREATE TABLE weekly_shift (
    employee_id bigint NOT NULL REFERENCES employee(id),
    weekday integer NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_minute integer NOT NULL CHECK (start_minute >= 0 AND start_minute % 15 = 0),
    end_minute integer NOT NULL CHECK (end_minute <= 1440 AND end_minute % 15 = 0 AND end_minute > start_minute),
    PRIMARY KEY (employee_id, weekday, start_minute)
);

CREATE TABLE closed_date (
    employee_id bigint NOT NULL REFERENCES employee(id),
    closed_on date NOT NULL,
    PRIMARY KEY (employee_id, closed_on)
);

CREATE TABLE account (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id bigint NOT NULL REFERENCES shop(id),
    employee_id bigint UNIQUE,
    username varchar(80) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    role varchar(10) NOT NULL,
    FOREIGN KEY (shop_id, employee_id) REFERENCES employee(shop_id, id),
    CHECK ((role = 'OWNER' AND employee_id IS NULL) OR (role = 'EMPLOYEE' AND employee_id IS NOT NULL))
);

CREATE UNIQUE INDEX account_one_owner ON account(shop_id) WHERE role = 'OWNER';

CREATE TABLE employee_invite (
    employee_id bigint PRIMARY KEY REFERENCES employee(id),
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);
