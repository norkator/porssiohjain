ALTER TABLE device
    ADD COLUMN mqtt_password_change_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mqtt_password_change_allowed_until TIMESTAMP WITH TIME ZONE;
