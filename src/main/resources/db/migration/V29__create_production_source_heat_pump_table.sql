CREATE TABLE production_source_heat_pump
(
    id                   BIGSERIAL PRIMARY KEY,
    production_source_id BIGINT         NOT NULL,
    device_id            BIGINT         NOT NULL,
    state_hex            TEXT           NOT NULL,
    control_action       VARCHAR(20)    NOT NULL,
    comparison_type      VARCHAR(20)    NOT NULL,
    trigger_kw           DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_prod_source_heat_pump_source FOREIGN KEY (production_source_id) REFERENCES production_source (id) ON DELETE CASCADE,
    CONSTRAINT fk_prod_source_heat_pump_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT uk_prod_source_heat_pump UNIQUE (production_source_id, device_id, control_action)
);
