CREATE TABLE wind_notification
(
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT         NOT NULL,
    name         VARCHAR(255)   NOT NULL,
    description  TEXT,
    rule_type    VARCHAR(40)    NOT NULL,
    threshold    NUMERIC(12, 2) NOT NULL,
    timezone     VARCHAR(255)   NOT NULL,
    enabled      BOOLEAN        NOT NULL DEFAULT TRUE,
    last_sent_at TIMESTAMP,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,
    CONSTRAINT fk_wind_notification_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);
CREATE INDEX idx_wind_notification_account ON wind_notification (account_id);
CREATE INDEX idx_wind_notification_enabled ON wind_notification (enabled);
