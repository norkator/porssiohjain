CREATE TABLE control_heat_pump
(
    id             BIGSERIAL PRIMARY KEY,
    control_id     BIGINT      NOT NULL,
    device_id      BIGINT      NOT NULL,
    state_hex      TEXT        NOT NULL,
    control_action VARCHAR(20) NOT NULL,
    CONSTRAINT fk_control_heat_pump_control FOREIGN KEY (control_id) REFERENCES control (id) ON DELETE CASCADE,
    CONSTRAINT fk_control_heat_pump_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT uk_control_heat_pump UNIQUE (control_id, device_id, control_action)
);
