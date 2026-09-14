ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS no_preheat_window_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS no_preheat_from TIME DEFAULT TIME '22:00';

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS no_preheat_to TIME DEFAULT TIME '05:00';
