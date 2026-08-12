ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS cheap_price_percentile NUMERIC (5, 4) NOT NULL DEFAULT 0.2500;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS expensive_price_percentile NUMERIC (5, 4) NOT NULL DEFAULT 0.7500;

DO
$$
BEGIN
    IF
NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_heating_planner_price_percentiles'
          AND table_name = 'heating_planner_settings'
    ) THEN
ALTER TABLE heating_planner_settings
    ADD CONSTRAINT chk_heating_planner_price_percentiles CHECK (
        cheap_price_percentile >= 0
            AND expensive_price_percentile <= 1
            AND cheap_price_percentile < expensive_price_percentile
        );
END IF;
END
$$;
