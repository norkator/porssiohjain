ALTER TABLE account
    ADD COLUMN email VARCHAR(50);

ALTER TABLE account
    ADD COLUMN notify_power_limit_exceeded BOOLEAN NOT NULL DEFAULT FALSE;
