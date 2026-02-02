ALTER TABLE power_limit
    ADD COLUMN last_total_kwh NUMERIC(12, 3),
    ADD COLUMN last_measured_at TIMESTAMPTZ;
