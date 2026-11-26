ALTER TABLE service
    ADD COLUMN price_mode varchar(12) NOT NULL DEFAULT 'INQUIRE',
    ADD COLUMN price_cents integer,
    ADD CONSTRAINT service_valid_price CHECK (
        (price_mode IN ('FREE', 'INQUIRE') AND price_cents IS NULL)
        OR (price_mode = 'FIXED' AND price_cents IS NOT NULL AND price_cents > 0)
    );
ALTER TABLE library ADD COLUMN cover text NOT NULL DEFAULT '';
