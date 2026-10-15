CREATE TABLE device_measurement
(
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    device_id        BIGINT NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    source           VARCHAR(32) NOT NULL,
    source_key       VARCHAR(128) NOT NULL,
    measurement_type VARCHAR(32) NOT NULL,
    measurement_key  VARCHAR(64) NOT NULL,
    "value"          NUMERIC(10, 3) NOT NULL,
    unit             VARCHAR(16) NOT NULL,
    measured_at      TIMESTAMP WITH TIME ZONE,
    observed_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_device_measurement_device_key_time
    ON device_measurement (device_id, measurement_type, measurement_key, observed_at DESC, id DESC);
CREATE INDEX idx_device_measurement_account_time
    ON device_measurement (account_id, observed_at DESC);

CREATE INDEX idx_device_measurement_received_time
    ON device_measurement (received_at);

