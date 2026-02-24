CREATE TABLE resource_sharing
(
    id                   BIGSERIAL PRIMARY KEY,
    sharer_account_id    BIGINT      NOT NULL,
    receiver_account_id  BIGINT      NOT NULL,
    resource_type        VARCHAR(50) NOT NULL,

    device_id            BIGINT,
    control_id           BIGINT,
    production_source_id BIGINT,
    power_limit_id       BIGINT,

    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled              BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_sharer_account
        FOREIGN KEY (sharer_account_id)
            REFERENCES account (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_receiver_account
        FOREIGN KEY (receiver_account_id)
            REFERENCES account (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_device
        FOREIGN KEY (device_id)
            REFERENCES device (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_control
        FOREIGN KEY (control_id)
            REFERENCES control (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_production_source
        FOREIGN KEY (production_source_id)
            REFERENCES production_source (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_power_limit
        FOREIGN KEY (power_limit_id)
            REFERENCES power_limit (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_resource_type_consistency CHECK (
        (resource_type = 'DEVICE' AND device_id IS NOT NULL AND control_id IS NULL AND production_source_id IS NULL AND
         power_limit_id IS NULL) OR
        (resource_type = 'CONTROL' AND control_id IS NOT NULL AND device_id IS NULL AND production_source_id IS NULL AND
         power_limit_id IS NULL) OR
        (resource_type = 'PRODUCTION_SOURCE' AND production_source_id IS NOT NULL AND device_id IS NULL AND
         control_id IS NULL AND power_limit_id IS NULL) OR
        (resource_type = 'POWER_LIMIT' AND power_limit_id IS NOT NULL AND device_id IS NULL AND control_id IS NULL AND
         production_source_id IS NULL)
        ),

    CONSTRAINT chk_valid_resource_type CHECK (
        resource_type IN ('DEVICE', 'CONTROL', 'PRODUCTION_SOURCE', 'POWER_LIMIT')
        ),

    CONSTRAINT chk_no_self_sharing CHECK (sharer_account_id <> receiver_account_id)
);

CREATE INDEX idx_resource_sharing_sharer_account ON resource_sharing (sharer_account_id);
CREATE INDEX idx_resource_sharing_receiver_account ON resource_sharing (receiver_account_id);
CREATE INDEX idx_resource_sharing_device ON resource_sharing (device_id) WHERE device_id IS NOT NULL;
CREATE INDEX idx_resource_sharing_control ON resource_sharing (control_id) WHERE control_id IS NOT NULL;
CREATE INDEX idx_resource_sharing_production_source ON resource_sharing (production_source_id) WHERE production_source_id IS NOT NULL;
CREATE INDEX idx_resource_sharing_power_limit ON resource_sharing (power_limit_id) WHERE power_limit_id IS NOT NULL;
CREATE INDEX idx_resource_sharing_enabled ON resource_sharing (enabled) WHERE enabled = TRUE;
CREATE INDEX idx_resource_sharing_resource_type ON resource_sharing (resource_type);


CREATE UNIQUE INDEX uniq_device_sharing
    ON resource_sharing (sharer_account_id, receiver_account_id, device_id) WHERE device_id IS NOT NULL;

CREATE UNIQUE INDEX uniq_control_sharing
    ON resource_sharing (sharer_account_id, receiver_account_id, control_id) WHERE control_id IS NOT NULL;

CREATE UNIQUE INDEX uniq_production_source_sharing
    ON resource_sharing (sharer_account_id, receiver_account_id, production_source_id) WHERE production_source_id IS NOT NULL;

CREATE UNIQUE INDEX uniq_power_limit_sharing
    ON resource_sharing (sharer_account_id, receiver_account_id, power_limit_id) WHERE power_limit_id IS NOT NULL;