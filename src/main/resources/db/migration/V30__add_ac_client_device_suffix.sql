ALTER TABLE device_ac_data
    ADD COLUMN ac_client_device_suffix VARCHAR(64);

UPDATE device_ac_data
SET ac_client_device_suffix = encode(gen_random_bytes(12), 'hex')
WHERE ac_client_device_suffix IS NULL;

ALTER TABLE device_ac_data
    ALTER COLUMN ac_client_device_suffix SET NOT NULL;
