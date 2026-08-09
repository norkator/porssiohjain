CREATE TABLE zigbee_device_measurement
(
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    device_id        BIGINT                   NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    gateway_id       UUID                     NOT NULL,
    zigbee_ieee      VARCHAR(16)              NOT NULL,
    profile          VARCHAR(64)              NOT NULL,
    measurement_type VARCHAR(32)              NOT NULL,
    measurement_key  VARCHAR(64)              NOT NULL,
    value            NUMERIC(10, 3)           NOT NULL,
    measured_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_zigbee_measurement_type CHECK (
        measurement_type IN ('TEMPERATURE', 'HUMIDITY', 'BATTERY_PERCENTAGE', 'THERMOSTAT_SETPOINT')
        )
);

CREATE INDEX idx_zigbee_measurement_device_type_time
    ON zigbee_device_measurement (device_id, measurement_type, measured_at DESC);
CREATE INDEX idx_zigbee_measurement_account_time
    ON zigbee_device_measurement (account_id, measured_at DESC);
CREATE INDEX idx_zigbee_measurement_gateway_ieee_time
    ON zigbee_device_measurement (gateway_id, zigbee_ieee, measured_at DESC);
