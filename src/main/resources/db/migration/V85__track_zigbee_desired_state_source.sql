ALTER TABLE zigbee_gateway_device
    ADD COLUMN desired_source VARCHAR(32);

ALTER TABLE zigbee_gateway_device
    ADD CONSTRAINT chk_zigbee_desired_source CHECK (
        desired_source IS NULL OR desired_source IN ('THERMOSTAT_CONTROL', 'HEATING_PLANNER')
        );
