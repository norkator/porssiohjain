ALTER TABLE heating_planner_settings
    ADD COLUMN last_automatic_plan_at       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_automatic_activation_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_automation_error        VARCHAR(1024);
