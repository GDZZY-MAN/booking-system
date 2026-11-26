ALTER TABLE reader_chat DROP CONSTRAINT reader_chat_status_check;
ALTER TABLE reader_chat ADD CONSTRAINT reader_chat_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'DISABLED', 'BLOCKED', 'CLOSED', 'EXPIRED'));

ALTER TABLE reader_chat
    ADD COLUMN resolution_reason varchar(40),
    ADD COLUMN resolved_at timestamptz,
    ADD COLUMN resolved_by_reader_id bigint REFERENCES reader_account(id);

COMMENT ON COLUMN reader_chat.expires_at IS 'Original shared-reservation endpoint; pending invitations are not auto-expired.';
