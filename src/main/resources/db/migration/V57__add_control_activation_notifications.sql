ALTER TABLE account
    ADD COLUMN notify_control_activated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE control_table
    ADD COLUMN activation_push_sent_at TIMESTAMP WITH TIME ZONE;
