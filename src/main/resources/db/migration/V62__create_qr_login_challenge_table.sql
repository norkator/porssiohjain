CREATE TABLE qr_login_challenge
(
    id                  BIGSERIAL PRIMARY KEY,
    challenge_id        UUID         NOT NULL UNIQUE,
    browser_secret_hash VARCHAR(255) NOT NULL,
    scan_secret_hash    VARCHAR(255) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    account_id          BIGINT       NULL REFERENCES account (id) ON DELETE CASCADE,
    created_ip          VARCHAR(255) NULL,
    browser_name        VARCHAR(255) NULL,
    time_zone           VARCHAR(128) NULL,
    created_at          TIMESTAMP    NOT NULL,
    expires_at          TIMESTAMP    NOT NULL,
    approved_at         TIMESTAMP    NULL,
    consumed_at         TIMESTAMP    NULL,
    CONSTRAINT chk_qr_login_status CHECK (status IN ('PENDING', 'APPROVED', 'CONSUMED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_qr_login_challenge_status_expires_at
    ON qr_login_challenge (status, expires_at);
