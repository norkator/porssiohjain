ALTER TABLE account
    ADD COLUMN device_limit INT;

ALTER TABLE account
    ADD CONSTRAINT account_device_limit_check
        CHECK (device_limit IS NULL OR device_limit >= 0);
