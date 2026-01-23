CREATE TABLE power_limit_history
(
    id             BIGSERIAL PRIMARY KEY,
    account_id     BIGINT         NOT NULL,
    power_limit_id BIGINT         NOT NULL,
    kilowatts      NUMERIC(10, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_plh_account
        FOREIGN KEY (account_id)
            REFERENCES account (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_plh_power_limit
        FOREIGN KEY (power_limit_id)
            REFERENCES power_limit (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_plh_power_limit_id
    ON power_limit_history (power_limit_id);

CREATE INDEX idx_plh_account_id
    ON power_limit_history (account_id);

CREATE INDEX idx_plh_created_at
    ON power_limit_history (created_at);
