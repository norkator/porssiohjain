ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS stove_loaded BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS stove_available_from TIME;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS stove_available_to TIME;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS wood_amount NUMERIC(10, 2) NOT NULL DEFAULT 8.00;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS wood_release_delay_minutes INTEGER NOT NULL DEFAULT 45;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS wood_release_duration_minutes INTEGER NOT NULL DEFAULT 360;
