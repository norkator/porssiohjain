CREATE TABLE zigbee_gateway_device
(
    id                   BIGSERIAL PRIMARY KEY,
    account_id           BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    device_id            BIGINT      NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    gateway_id           UUID        NOT NULL,
    zigbee_ieee          VARCHAR(16) NOT NULL,
    profile              VARCHAR(64) NOT NULL,
    custom_name          VARCHAR(64),
    desired_temperature  NUMERIC(10, 2),
    desired_mode         VARCHAR(8),
    desired_version      BIGINT      NOT NULL DEFAULT 0,
    desired_at           TIMESTAMP WITH TIME ZONE,
    desired_expires_at   TIMESTAMP WITH TIME ZONE,
    applied_version      BIGINT      NOT NULL DEFAULT 0,
    reported_temperature NUMERIC(10, 2),
    reported_setpoint    NUMERIC(10, 2),
    reported_mode        VARCHAR(8),
    last_seen            TIMESTAMP WITH TIME ZONE,
    last_error           VARCHAR(512),
    CONSTRAINT uk_zigbee_gateway_ieee UNIQUE (gateway_id, zigbee_ieee),
    CONSTRAINT uk_zigbee_cloud_device UNIQUE (device_id)
);

CREATE INDEX idx_zigbee_gateway_account ON zigbee_gateway_device (account_id);
