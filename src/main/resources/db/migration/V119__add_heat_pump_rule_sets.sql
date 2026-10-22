ALTER TABLE control_heat_pump
    ADD COLUMN active_from TIME,
    ADD COLUMN active_until TIME,
    ADD COLUMN outside_active_time BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rule_order INTEGER;
