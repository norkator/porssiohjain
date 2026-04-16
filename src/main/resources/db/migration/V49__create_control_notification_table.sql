CREATE TABLE control_notification
(
    id             BIGSERIAL PRIMARY KEY,
    control_id     BIGINT        NOT NULL,
    account_id     BIGINT        NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    description    TEXT,
    active_from    TIME          NOT NULL,
    active_to      TIME          NOT NULL,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    cheapest_hours NUMERIC(5, 2) NOT NULL DEFAULT 0,
    last_sent_at   TIMESTAMP              DEFAULT NULL,
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP     NOT NULL,
    CONSTRAINT fk_control_notification_control FOREIGN KEY (control_id) REFERENCES control (id) ON DELETE CASCADE,
    CONSTRAINT fk_control_notification_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE INDEX idx_control_notification_control_account ON control_notification (control_id, account_id);
CREATE INDEX idx_control_notification_enabled ON control_notification (enabled);
