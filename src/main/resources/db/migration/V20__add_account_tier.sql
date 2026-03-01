ALTER TABLE account
    ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'FREE';

ALTER TABLE account
    ADD CONSTRAINT account_tier_check
        CHECK (tier IN ('FREE', 'PRO', 'BUSINESS'));