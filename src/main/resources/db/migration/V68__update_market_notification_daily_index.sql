DROP INDEX IF EXISTS idx_market_notification_enabled_unsent;

CREATE INDEX IF NOT EXISTS idx_market_notification_enabled
    ON market_notification (enabled);
