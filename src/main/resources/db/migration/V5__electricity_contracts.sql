CREATE TABLE electricity_contract
(
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT                   NOT NULL,
    name         VARCHAR(255)             NOT NULL,
    type         VARCHAR(32)              NOT NULL,
    basic_fee    NUMERIC(10, 2),
    night_price  NUMERIC(12, 6),
    day_price    NUMERIC(12, 6),
    static_price NUMERIC(12, 6),
    tax_percent  NUMERIC(5, 2),
    tax_amount   NUMERIC(12, 6),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_electricity_contract_account
        FOREIGN KEY (account_id)
            REFERENCES account (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_electricity_contract_account
    ON electricity_contract (account_id);

CREATE INDEX idx_electricity_contract_type
    ON electricity_contract (type);