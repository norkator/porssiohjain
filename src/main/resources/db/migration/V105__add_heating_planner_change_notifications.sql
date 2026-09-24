ALTER TABLE heating_planner_settings
    ADD COLUMN notify_thermostat_changes BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notify_heat_pump_changes BOOLEAN NOT NULL DEFAULT FALSE;
