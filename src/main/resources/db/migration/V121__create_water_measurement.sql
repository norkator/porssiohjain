ALTER TABLE water_meter RENAME COLUMN payer_number TO username;
ALTER TABLE water_meter RENAME COLUMN water_meter_number TO password;

ALTER TABLE water_meter
    ADD COLUMN provider VARCHAR(40) NOT NULL DEFAULT 'WRM_SYSTEMS_WMD';
ALTER TABLE water_meter
    ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE water_meter
    ADD COLUMN push_frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY';
ALTER TABLE water_meter
    ADD COLUMN push_time TIME NOT NULL DEFAULT '12:05:00';
ALTER TABLE water_meter
    ADD COLUMN push_weekday INTEGER NOT NULL DEFAULT 1;
ALTER TABLE water_meter
    ADD COLUMN push_timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Helsinki';
ALTER TABLE water_meter
    ADD COLUMN last_push_sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE water_meter
    ADD CONSTRAINT water_meter_push_frequency_check CHECK (push_frequency IN ('DAILY', 'WEEKLY'));
ALTER TABLE water_meter
    ADD CONSTRAINT water_meter_push_weekday_check CHECK (push_weekday BETWEEN 1 AND 7);

CREATE TABLE water_measurement
(
    id                 BIGSERIAL PRIMARY KEY,
    water_meter_id     BIGINT                   NOT NULL REFERENCES water_meter (id) ON DELETE CASCADE,
    total_cubic_meters NUMERIC(20, 6)           NOT NULL CHECK (total_cubic_meters >= 0),
    observed_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (water_meter_id, observed_at)
);