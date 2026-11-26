CREATE TABLE reader_social_preferences (
    reader_id bigint PRIMARY KEY REFERENCES reader_account(id),
    nickname varchar(30) NOT NULL DEFAULT '同馆读者',
    chat_enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE reader_block (
    blocker_id bigint NOT NULL REFERENCES reader_account(id),
    blocked_id bigint NOT NULL REFERENCES reader_account(id),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE TABLE quiet_reminder (
    id uuid PRIMARY KEY,
    sender_id bigint NOT NULL REFERENCES reader_account(id),
    recipient_id bigint NOT NULL REFERENCES reader_account(id),
    reservation_id uuid NOT NULL REFERENCES seat_reservation(id),
    kind varchar(20) NOT NULL CHECK (kind IN ('VOICE', 'HEADPHONES', 'ITEMS')),
    created_at timestamptz NOT NULL,
    acknowledged_at timestamptz,
    CHECK (sender_id <> recipient_id)
);
CREATE INDEX quiet_reminder_recipient ON quiet_reminder(recipient_id, created_at);
CREATE INDEX quiet_reminder_rate ON quiet_reminder(reservation_id, created_at);
CREATE INDEX quiet_reminder_sender ON quiet_reminder(sender_id, created_at);
CREATE TABLE reader_chat (
    id uuid PRIMARY KEY,
    sender_id bigint NOT NULL REFERENCES reader_account(id),
    recipient_id bigint NOT NULL REFERENCES reader_account(id),
    sender_reservation uuid NOT NULL REFERENCES seat_reservation(id),
    recipient_reservation uuid NOT NULL REFERENCES seat_reservation(id),
    status varchar(12) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CLOSED', 'EXPIRED')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    sender_seen bigint NOT NULL DEFAULT 0,
    recipient_seen bigint NOT NULL DEFAULT 0,
    UNIQUE (sender_reservation, recipient_id),
    CHECK (sender_id <> recipient_id)
);
CREATE INDEX reader_chat_recipient ON reader_chat(recipient_id, created_at);
CREATE INDEX reader_chat_sender ON reader_chat(sender_id, created_at);
CREATE TABLE reader_chat_message (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id uuid NOT NULL REFERENCES reader_chat(id),
    sender_id bigint NOT NULL REFERENCES reader_account(id),
    body varchar(500) NOT NULL,
    request_key uuid NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (chat_id, sender_id, request_key)
);
CREATE INDEX reader_chat_message_history ON reader_chat_message(chat_id, id);
