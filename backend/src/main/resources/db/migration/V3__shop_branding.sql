CREATE TABLE shop_branding (
    shop_id bigint NOT NULL REFERENCES shop(id),
    published boolean NOT NULL,
    theme varchar(12) NOT NULL CHECK (theme IN ('forest', 'plum', 'ocean', 'clay')),
    address varchar(180) NOT NULL,
    business_note varchar(300) NOT NULL,
    booking_note varchar(500) NOT NULL,
    logo text NOT NULL CHECK (length(logo) <= 273100),
    cover text NOT NULL CHECK (length(cover) <= 546200),
    PRIMARY KEY (shop_id, published)
);
