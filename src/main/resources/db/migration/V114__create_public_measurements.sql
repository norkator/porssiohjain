ALTER TABLE account ADD COLUMN public_measurements_uuid UUID UNIQUE;

CREATE TABLE public_measurement
(
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    device_id        BIGINT NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    measurement_type VARCHAR(32) NOT NULL,
    measurement_key  VARCHAR(64) NOT NULL,
    UNIQUE (account_id, device_id, measurement_type, measurement_key)
);
