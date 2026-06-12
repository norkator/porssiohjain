ALTER TABLE device
    ADD COLUMN serial_number VARCHAR(128),
    ADD COLUMN chip_id VARCHAR(32),
    ADD COLUMN factory_device_status VARCHAR(32) NOT NULL DEFAULT 'CLAIMED',
    ADD COLUMN claim_code VARCHAR(64),
    ADD COLUMN claimed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE device
    ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE device
    ADD CONSTRAINT uk_device_serial_number UNIQUE (serial_number),
    ADD CONSTRAINT uk_device_claim_code UNIQUE (claim_code);

ALTER TABLE factory_test_run
DROP
CONSTRAINT fk_factory_test_run_device;

DELETE
FROM factory_test_run;

ALTER TABLE factory_test_run
    ADD CONSTRAINT fk_factory_test_run_device
        FOREIGN KEY (factory_device_id) REFERENCES device (id) ON DELETE CASCADE;

ALTER TABLE ota_deployment
DROP
CONSTRAINT fk_ota_deployment_factory_device;

DELETE
FROM ota_deployment
WHERE factory_device_id IS NOT NULL;

ALTER TABLE ota_deployment
    ADD CONSTRAINT fk_ota_deployment_factory_device
        FOREIGN KEY (factory_device_id) REFERENCES device (id) ON DELETE CASCADE;

DROP TABLE factory_device;
