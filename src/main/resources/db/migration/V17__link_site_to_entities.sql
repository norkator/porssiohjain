ALTER TABLE control
    ADD COLUMN site_id BIGINT;

ALTER TABLE control
    ADD CONSTRAINT fk_control_site
        FOREIGN KEY (site_id) REFERENCES site (id)
            ON DELETE SET NULL;

CREATE INDEX idx_control_site ON control (site_id);

ALTER TABLE power_limit
    ADD COLUMN site_id BIGINT;

ALTER TABLE power_limit
    ADD CONSTRAINT fk_power_limit_site
        FOREIGN KEY (site_id) REFERENCES site (id)
            ON DELETE SET NULL;

CREATE INDEX idx_power_limit_site ON power_limit (site_id);

ALTER TABLE production_source
    ADD COLUMN site_id BIGINT;

ALTER TABLE production_source
    ADD CONSTRAINT fk_production_source_site
        FOREIGN KEY (site_id) REFERENCES site (id)
            ON DELETE SET NULL;

CREATE INDEX idx_production_source_site ON production_source (site_id);
