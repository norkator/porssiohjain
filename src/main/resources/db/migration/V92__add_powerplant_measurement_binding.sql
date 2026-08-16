ALTER TABLE powerplant_element
    ADD COLUMN measurement_type VARCHAR(32),
    ADD COLUMN measurement_key  VARCHAR(64);

ALTER TABLE powerplant_element
    ADD CONSTRAINT chk_powerplant_measurement_type CHECK (
        measurement_type IS NULL OR
        measurement_type IN ('TEMPERATURE', 'HUMIDITY', 'BATTERY_PERCENTAGE', 'THERMOSTAT_SETPOINT')
        );
