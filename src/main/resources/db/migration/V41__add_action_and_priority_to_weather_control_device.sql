ALTER TABLE weather_control_device
    ADD COLUMN control_action VARCHAR(32) NOT NULL DEFAULT 'TURN_ON';

ALTER TABLE weather_control_device
    ADD COLUMN priority_rule BOOLEAN NOT NULL DEFAULT FALSE;
