CREATE TABLE billing_subscription
(
    id                    BIGSERIAL PRIMARY KEY,
    account_id            BIGINT       NOT NULL,
    provider              VARCHAR(30)  NOT NULL,
    product_id            VARCHAR(100) NOT NULL,
    purchase_token        VARCHAR(512) NOT NULL,
    tier                  VARCHAR(20)  NOT NULL,
    status                VARCHAR(40)  NOT NULL,
    expires_at            TIMESTAMPTZ,
    auto_renewing         BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged          BOOLEAN      NOT NULL DEFAULT FALSE,
    linked_purchase_token VARCHAR(512),
    last_verified_at      TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_billing_subscription_account
        FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    CONSTRAINT uq_billing_subscription_purchase_token UNIQUE (purchase_token),
    CONSTRAINT billing_subscription_provider_check CHECK (provider IN ('GOOGLE_PLAY')),
    CONSTRAINT billing_subscription_tier_check CHECK (tier IN ('PRO', 'BUSINESS')),
    CONSTRAINT billing_subscription_status_check CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELED', 'VERIFICATION_FAILED'))
);

CREATE INDEX idx_billing_subscription_account ON billing_subscription (account_id);
CREATE UNIQUE INDEX idx_billing_subscription_purchase_token ON billing_subscription (purchase_token);
