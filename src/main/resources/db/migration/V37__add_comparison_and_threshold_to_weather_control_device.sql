ALTER TABLE weather_control_device
    ADD COLUMN comparison_type VARCHAR(32);

ALTER TABLE weather_control_device
    ADD COLUMN threshold_value NUMERIC(19, 4);
