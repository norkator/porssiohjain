CREATE TABLE control_savings_month
(
    id            BIGSERIAL PRIMARY KEY,
    account_id    BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    month_start   DATE        NOT NULL,
    timezone      VARCHAR(64) NOT NULL,
    period_start  TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end    TIMESTAMP WITH TIME ZONE NOT NULL,
    summary_json  TEXT        NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_control_savings_month UNIQUE (account_id, month_start, timezone),
    CONSTRAINT chk_control_savings_month_period CHECK (period_end > period_start)
);

-- Records the boundary beyond which arbitrary partial-month recalculation is unavailable.
ALTER TABLE account ADD COLUMN control_history_deleted_before TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_qr_login_challenge_expires_at ON qr_login_challenge (expires_at);
CREATE INDEX idx_push_notification_token_invalidated_at ON push_notification_token (invalidated_at);
CREATE INDEX idx_control_table_end_time ON control_table (end_time);
CREATE INDEX idx_control_account ON control (account_id);
