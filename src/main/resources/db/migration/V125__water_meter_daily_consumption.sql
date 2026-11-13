ALTER TABLE water_meter
    ADD COLUMN metering_point_code VARCHAR(255);
ALTER TABLE water_meter ADD COLUMN baseline_cubic_meters NUMERIC(20,6);
ALTER TABLE water_meter ADD COLUMN baseline_date DATE;
ALTER TABLE water_meter ADD COLUMN consumption_through DATE;

ALTER TABLE water_measurement
    ADD COLUMN consumption_date DATE;
ALTER TABLE water_measurement ADD COLUMN consumption_cubic_meters NUMERIC(20,6);
CREATE UNIQUE INDEX water_measurement_consumption_day
    ON water_measurement (water_meter_id, consumption_date);

ALTER TABLE water_measurement
    DROP CONSTRAINT IF EXISTS water_measurement_water_meter_id_observed_at_key;
ALTER TABLE water_measurement ALTER COLUMN total_cubic_meters DROP NOT NULL;
