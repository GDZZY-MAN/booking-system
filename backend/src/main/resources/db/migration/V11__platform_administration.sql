CREATE TABLE platform_admin (
    username varchar(80) PRIMARY KEY,
    password_hash varchar(100) NOT NULL
);
ALTER TABLE shop
    ADD COLUMN resource_kind varchar(12) NOT NULL DEFAULT 'MERCHANT' CHECK (resource_kind IN ('MERCHANT', 'CAMPUS')),
    ADD COLUMN merchant_state varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK (merchant_state IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    ADD COLUMN review_note varchar(500) NOT NULL DEFAULT '';
UPDATE shop SET resource_kind = 'CAMPUS' WHERE id IN (SELECT shop_id FROM library);
-- Previously published merchants remain available; no historical orders or links are removed.
UPDATE shop SET merchant_state = 'APPROVED' WHERE published;
CREATE TABLE merchant_moderation_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id bigint NOT NULL REFERENCES shop(id),
    actor varchar(80) NOT NULL,
    action varchar(16) NOT NULL,
    note varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
