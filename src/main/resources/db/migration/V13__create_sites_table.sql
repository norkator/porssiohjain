CREATE TABLE site
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    type       VARCHAR(50) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    account_id BIGINT      NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    CONSTRAINT fk_site_account
        FOREIGN KEY (account_id)
            REFERENCES account (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_site_account_id ON site (account_id);
CREATE INDEX idx_site_type ON site (type);
