CREATE TABLE weather_control_device
(
    id                 BIGSERIAL PRIMARY KEY,
    weather_control_id BIGINT      NOT NULL,
    device_id          BIGINT      NOT NULL,
    device_channel     INTEGER     NOT NULL,
    weather_metric     VARCHAR(32) NOT NULL,
    CONSTRAINT fk_weather_control_device_control
        FOREIGN KEY (weather_control_id)
            REFERENCES weather_control (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_weather_control_device_device
        FOREIGN KEY (device_id)
            REFERENCES device (id)
            ON DELETE CASCADE,
    CONSTRAINT uk_weather_control_device_unique
        UNIQUE (weather_control_id, device_id, device_channel)
);

CREATE INDEX idx_weather_control_device_control_id ON weather_control_device (weather_control_id);
CREATE INDEX idx_weather_control_device_device_id ON weather_control_device (device_id);

CREATE TABLE weather_control_heat_pump
(
    id                 BIGSERIAL PRIMARY KEY,
    weather_control_id BIGINT         NOT NULL,
    device_id          BIGINT         NOT NULL,
    state_hex          TEXT           NOT NULL,
    weather_metric     VARCHAR(32)    NOT NULL,
    comparison_type    VARCHAR(32)    NOT NULL,
    threshold_value    NUMERIC(19, 4) NOT NULL,
    CONSTRAINT fk_weather_control_heat_pump_control
        FOREIGN KEY (weather_control_id)
            REFERENCES weather_control (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_weather_control_heat_pump_device
        FOREIGN KEY (device_id)
            REFERENCES device (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_weather_control_heat_pump_control_id ON weather_control_heat_pump (weather_control_id);
CREATE INDEX idx_weather_control_heat_pump_device_id ON weather_control_heat_pump (device_id);
