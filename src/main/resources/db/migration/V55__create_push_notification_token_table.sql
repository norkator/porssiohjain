CREATE TABLE push_notification_token
(
    id             BIGSERIAL PRIMARY KEY,
    account_id     BIGINT       NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    platform       VARCHAR(32)  NOT NULL,
    token          VARCHAR(512) NOT NULL,
    device_name    VARCHAR(120),
    last_seen_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    invalidated_at TIMESTAMP WITHOUT TIME ZONE,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_push_notification_token_token
    ON push_notification_token (token);

CREATE INDEX idx_push_notification_token_account_active
    ON push_notification_token (account_id, invalidated_at);
