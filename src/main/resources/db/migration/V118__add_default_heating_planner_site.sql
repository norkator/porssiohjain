ALTER TABLE account
    ADD COLUMN default_heating_planner_site_id BIGINT REFERENCES site (id) ON DELETE SET NULL;

CREATE INDEX idx_account_default_heating_planner_site ON account (default_heating_planner_site_id);
