ALTER TABLE library
    ADD COLUMN deleted_at timestamptz,
    ADD CONSTRAINT archived_space_is_closed CHECK (deleted_at IS NULL OR NOT published);
