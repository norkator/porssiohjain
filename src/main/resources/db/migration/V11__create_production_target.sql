CREATE TABLE production_target
(
    id                   BIGSERIAL PRIMARY KEY,
    production_source_id BIGINT                   NOT NULL,
    device_id            BIGINT                   NOT NULL,
    device_channel       INTEGER                  NOT NULL,
    trigger_kw           NUMERIC(10, 2)           NOT NULL,
    comparison_type      VARCHAR(20)              NOT NULL,
    action               VARCHAR(20)              NOT NULL,
    enabled              BOOLEAN                  NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_production_target_source
        FOREIGN KEY (production_source_id)
            REFERENCES production_source (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_production_target_device
        FOREIGN KEY (device_id)
            REFERENCES device (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_production_target_source
    ON production_target (production_source_id);

CREATE INDEX idx_production_target_device
    ON production_target (device_id);

CREATE INDEX idx_production_target_enabled
    ON production_target (enabled);
