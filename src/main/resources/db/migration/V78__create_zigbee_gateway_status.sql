CREATE TABLE zigbee_gateway_status
(
    id                  BIGSERIAL PRIMARY KEY,
    account_id          BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    gateway_id          UUID                     NOT NULL UNIQUE,
    last_seen           TIMESTAMP WITH TIME ZONE NOT NULL,
    offline             BOOLEAN                  NOT NULL DEFAULT FALSE,
    offline_detected_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_zigbee_gateway_status_offline_last_seen
    ON zigbee_gateway_status (offline, last_seen);
