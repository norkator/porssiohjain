-- Latest-per-series lists must not compare each retained row with newer history rows.
CREATE INDEX idx_device_measurement_account_series_latest
    ON device_measurement (account_id, device_id, measurement_type, measurement_key, observed_at DESC, id DESC);

CREATE INDEX idx_zigbee_measurement_account_series_latest
    ON zigbee_device_measurement (account_id, zigbee_ieee, measurement_type, measurement_key, measured_at DESC, id DESC);

-- Current sensor readings, private/public history and thermal-learning pages use device/type/key order.
CREATE INDEX idx_zigbee_measurement_device_key_time
    ON zigbee_device_measurement (device_id, measurement_type, measurement_key, measured_at DESC, id DESC);
