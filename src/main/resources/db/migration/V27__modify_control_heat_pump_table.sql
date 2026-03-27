ALTER TABLE control_heat_pump DROP CONSTRAINT uk_control_heat_pump;
ALTER TABLE control_heat_pump
    ADD COLUMN comparison_type VARCHAR(20);
ALTER TABLE control_heat_pump
    ADD COLUMN price_limit DECIMAL(19, 4);
