CREATE TABLE control_thermostat
(
    id                       BIGSERIAL PRIMARY KEY,
    control_id               BIGINT  NOT NULL REFERENCES control (id) ON DELETE CASCADE,
    device_id                BIGINT  NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    thermostat_channel       INTEGER NOT NULL,
    curve_json               TEXT    NOT NULL,
    min_temperature          DECIMAL(10, 2),
    max_temperature          DECIMAL(10, 2),
    fallback_temperature     DECIMAL(10, 2),
    estimated_power_kw       DECIMAL(10, 3),
    enabled                  BOOLEAN NOT NULL DEFAULT TRUE,
    last_applied_temperature DECIMAL(10, 2),
    last_applied_at          TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_control_thermostat_control_device_channel UNIQUE (control_id, device_id, thermostat_channel)
);
