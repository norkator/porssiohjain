ALTER TABLE account
    ADD COLUMN email_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN push_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;
