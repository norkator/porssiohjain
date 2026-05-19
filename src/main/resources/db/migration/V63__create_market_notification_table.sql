CREATE TABLE market_notification
(
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT         NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    description     TEXT,
    metric          VARCHAR(30)    NOT NULL,
    comparison_type VARCHAR(30)    NOT NULL,
    threshold_price NUMERIC(10, 4) NOT NULL,
    active_from     TIME           NOT NULL,
    active_to       TIME           NOT NULL,
    timezone        VARCHAR(255)   NOT NULL,
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    last_sent_at    TIMESTAMP               DEFAULT NULL,
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,
    CONSTRAINT fk_market_notification_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE INDEX idx_market_notification_account ON market_notification (account_id);
CREATE INDEX idx_market_notification_enabled_unsent ON market_notification (enabled, last_sent_at);
