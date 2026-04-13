ALTER TABLE resource_sharing
    ADD COLUMN weather_control_id BIGINT;

ALTER TABLE resource_sharing
    ADD CONSTRAINT fk_weather_control
        FOREIGN KEY (weather_control_id)
            REFERENCES weather_control (id)
            ON DELETE CASCADE;

ALTER TABLE resource_sharing
DROP
CONSTRAINT chk_resource_type_consistency;

ALTER TABLE resource_sharing
    ADD CONSTRAINT chk_resource_type_consistency CHECK (
        (resource_type = 'DEVICE' AND device_id IS NOT NULL AND control_id IS NULL AND production_source_id IS NULL AND
         power_limit_id IS NULL AND weather_control_id IS NULL) OR
        (resource_type = 'CONTROL' AND control_id IS NOT NULL AND device_id IS NULL AND production_source_id IS NULL AND
         power_limit_id IS NULL AND weather_control_id IS NULL) OR
        (resource_type = 'PRODUCTION_SOURCE' AND production_source_id IS NOT NULL AND device_id IS NULL AND
         control_id IS NULL AND power_limit_id IS NULL AND weather_control_id IS NULL) OR
        (resource_type = 'POWER_LIMIT' AND power_limit_id IS NOT NULL AND device_id IS NULL AND control_id IS NULL AND
         production_source_id IS NULL AND weather_control_id IS NULL) OR
        (resource_type = 'WEATHER_CONTROL' AND weather_control_id IS NOT NULL AND device_id IS NULL AND
         control_id IS NULL AND
         production_source_id IS NULL AND power_limit_id IS NULL)
        );

ALTER TABLE resource_sharing
DROP
CONSTRAINT chk_valid_resource_type;

ALTER TABLE resource_sharing
    ADD CONSTRAINT chk_valid_resource_type CHECK (
        resource_type IN ('DEVICE', 'CONTROL', 'PRODUCTION_SOURCE', 'POWER_LIMIT', 'WEATHER_CONTROL')
        );

CREATE INDEX idx_resource_sharing_weather_control ON resource_sharing (weather_control_id) WHERE weather_control_id IS NOT NULL;

CREATE UNIQUE INDEX uniq_weather_control_sharing
    ON resource_sharing (sharer_account_id, receiver_account_id, weather_control_id) WHERE weather_control_id IS NOT NULL;
