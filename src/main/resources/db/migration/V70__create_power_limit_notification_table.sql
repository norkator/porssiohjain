CREATE TABLE power_limit_notification
(
    id             BIGSERIAL PRIMARY KEY,
    power_limit_id BIGINT         NOT NULL,
    account_id     BIGINT         NOT NULL,
    name           VARCHAR(255)   NOT NULL,
    description    TEXT,
    active_from    TIME           NOT NULL,
    active_to      TIME           NOT NULL,
    enabled        BOOLEAN        NOT NULL DEFAULT TRUE,
    trigger_kw     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    last_sent_at   TIMESTAMP               DEFAULT NULL,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL,
    CONSTRAINT fk_power_limit_notification_limit FOREIGN KEY (power_limit_id) REFERENCES power_limit (id) ON DELETE CASCADE,
    CONSTRAINT fk_power_limit_notification_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE INDEX idx_power_limit_notification_limit_account ON power_limit_notification (power_limit_id, account_id);
CREATE INDEX idx_power_limit_notification_enabled ON power_limit_notification (enabled);
