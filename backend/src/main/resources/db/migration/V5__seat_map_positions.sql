ALTER TABLE library_seat ADD COLUMN floor integer CHECK (floor BETWEEN 1 AND 20);
ALTER TABLE library_seat ADD COLUMN map_row integer;
ALTER TABLE library_seat ADD COLUMN map_column integer;
ALTER TABLE library_seat ADD CONSTRAINT seat_map_coordinates CHECK (
    (map_row IS NULL AND map_column IS NULL) OR
    (map_row IS NOT NULL AND map_column IS NOT NULL AND map_row BETWEEN 1 AND 20 AND map_column BETWEEN 1 AND 12)
);
CREATE UNIQUE INDEX seat_map_position ON library_seat(shop_id, COALESCE(floor, 0), map_row, map_column)
    WHERE map_row IS NOT NULL;
