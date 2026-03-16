ALTER TABLE device
    ADD COLUMN last_telemetry TEXT,
    ADD COLUMN online BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mqtt_username VARCHAR(255) UNIQUE,
    ADD COLUMN mqtt_password VARCHAR(255);

CREATE INDEX idx_device_mqtt_username ON device (mqtt_username);