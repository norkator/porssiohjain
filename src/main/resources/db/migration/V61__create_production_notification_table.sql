CREATE TABLE production_notification
(
    id                   BIGSERIAL PRIMARY KEY,
    production_source_id BIGINT         NOT NULL,
    account_id           BIGINT         NOT NULL,
    name                 VARCHAR(255)   NOT NULL,
    description          TEXT,
    active_from          TIME           NOT NULL,
    active_to            TIME           NOT NULL,
    enabled              BOOLEAN        NOT NULL DEFAULT TRUE,
    trigger_kw           NUMERIC(10, 2) NOT NULL DEFAULT 0,
    last_sent_at         TIMESTAMP               DEFAULT NULL,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    CONSTRAINT fk_production_notification_source FOREIGN KEY (production_source_id) REFERENCES production_source (id) ON DELETE CASCADE,
    CONSTRAINT fk_production_notification_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE INDEX idx_production_notification_source_account ON production_notification (production_source_id, account_id);
CREATE INDEX idx_production_notification_enabled ON production_notification (enabled);
