ALTER TABLE control_notification
    ADD COLUMN send_earlier_minutes INTEGER NOT NULL DEFAULT 0;
