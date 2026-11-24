ALTER TABLE push_notification_token
    ADD COLUMN stove_actions_supported BOOLEAN NOT NULL DEFAULT FALSE;
