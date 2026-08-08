ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS tax_percent NUMERIC (5, 2) NOT NULL DEFAULT 25.50;

ALTER TABLE heating_planner_settings
    ADD COLUMN IF NOT EXISTS transfer_contract_id BIGINT;

DO
$$
BEGIN
        IF
NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE constraint_name = 'fk_heating_planner_settings_transfer_contract'
              AND table_name = 'heating_planner_settings'
        ) THEN
ALTER TABLE heating_planner_settings
    ADD CONSTRAINT fk_heating_planner_settings_transfer_contract
        FOREIGN KEY (transfer_contract_id) REFERENCES electricity_contract (id) ON DELETE SET NULL;
END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_heating_planner_settings_transfer_contract
    ON heating_planner_settings (transfer_contract_id) WHERE transfer_contract_id IS NOT NULL;
