ALTER TABLE control
    ADD COLUMN energy_contract_id BIGINT,
    ADD COLUMN transfer_contract_id BIGINT;

ALTER TABLE control
    ADD CONSTRAINT fk_control_energy_contract
        FOREIGN KEY (energy_contract_id)
            REFERENCES electricity_contract (id)
            ON DELETE SET NULL;

ALTER TABLE control
    ADD CONSTRAINT fk_control_transfer_contract
        FOREIGN KEY (transfer_contract_id)
            REFERENCES electricity_contract (id)
            ON DELETE SET NULL;
