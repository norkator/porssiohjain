ALTER TABLE power_limit
    ADD COLUMN energy_contract_id BIGINT,
    ADD COLUMN transfer_contract_id BIGINT;

ALTER TABLE power_limit
    ADD CONSTRAINT fk_power_limit_energy_contract
        FOREIGN KEY (energy_contract_id)
            REFERENCES electricity_contract (id)
            ON DELETE SET NULL;

ALTER TABLE power_limit
    ADD CONSTRAINT fk_power_limit_transfer_contract
        FOREIGN KEY (transfer_contract_id)
            REFERENCES electricity_contract (id)
            ON DELETE SET NULL;

CREATE INDEX idx_power_limit_energy_contract_id
    ON power_limit (energy_contract_id);

CREATE INDEX idx_power_limit_transfer_contract_id
    ON power_limit (transfer_contract_id);
