ALTER TABLE device
    ADD COLUMN mqtt_device_profile VARCHAR(64) NOT NULL DEFAULT 'GENERIC_RELAY';

ALTER TABLE factory_device
    ADD COLUMN mqtt_device_profile VARCHAR(64) NOT NULL DEFAULT 'GENERIC_RELAY',
    ADD COLUMN claim_code VARCHAR(64),
    ADD COLUMN claimed_at TIMESTAMP WITH TIME ZONE;

UPDATE factory_device
SET claim_code = 'FD-' || id
WHERE claim_code IS NULL;

ALTER TABLE factory_device
    ALTER COLUMN claim_code SET NOT NULL;

ALTER TABLE factory_device
    ADD CONSTRAINT uk_factory_device_claim_code UNIQUE (claim_code);
