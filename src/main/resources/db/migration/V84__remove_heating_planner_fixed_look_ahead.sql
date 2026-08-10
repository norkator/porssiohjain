ALTER TABLE heating_planner_settings
    DROP CONSTRAINT IF EXISTS chk_heating_planner_preheat_look_ahead_positive;

ALTER TABLE heating_planner_settings
    DROP COLUMN IF EXISTS preheat_look_ahead_minutes;
