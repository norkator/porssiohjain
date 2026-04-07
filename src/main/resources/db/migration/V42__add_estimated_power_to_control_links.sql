ALTER TABLE control_device
    ADD COLUMN estimated_power_kw NUMERIC(10, 3);

ALTER TABLE control_heat_pump
    ADD COLUMN estimated_power_kw NUMERIC(10, 3);
