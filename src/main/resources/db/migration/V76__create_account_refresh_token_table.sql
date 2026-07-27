CREATE TABLE account_refresh_token
(
    id          BIGSERIAL PRIMARY KEY,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    account_id  BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_account_refresh_token_account_id
    ON account_refresh_token (account_id);

CREATE INDEX idx_account_refresh_token_expires_at
    ON account_refresh_token (expires_at);
