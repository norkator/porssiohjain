CREATE TABLE production_source_device
(
    id                   BIGSERIAL PRIMARY KEY,
    production_source_id BIGINT                   NOT NULL,
    device_id            BIGINT                   NOT NULL,
    device_channel       INT                      NOT NULL,
    trigger_kw           NUMERIC(10, 2)           NOT NULL,
    comparison_type      VARCHAR(20)              NOT NULL,
    action               VARCHAR(20)              NOT NULL,
    enabled              BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_production_source_device UNIQUE (production_source_id, device_id, device_channel),
    CONSTRAINT fk_psd_production_source FOREIGN KEY (production_source_id)
        REFERENCES production_source (id) ON DELETE CASCADE,
    CONSTRAINT fk_psd_device FOREIGN KEY (device_id)
        REFERENCES device (id) ON DELETE CASCADE
);

CREATE INDEX idx_production_source_device_source_id
    ON production_source_device (production_source_id);

CREATE INDEX idx_production_source_device_device_id
    ON production_source_device (device_id);

CREATE INDEX idx_production_source_device_channel
    ON production_source_device (device_channel);
