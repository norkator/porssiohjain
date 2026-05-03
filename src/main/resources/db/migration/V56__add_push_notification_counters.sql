ALTER TABLE account
    ADD COLUMN weekly_push_notification_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN weekly_push_notification_week_start DATE;
