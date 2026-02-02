ALTER TABLE power_limit
    ADD COLUMN limit_interval_minutes INTEGER NOT NULL DEFAULT 60;
