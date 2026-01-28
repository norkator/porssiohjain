ALTER TABLE power_limit
    ADD COLUMN peak_kw NUMERIC(10, 2) NOT NULL DEFAULT 0;

CREATE TABLE production_source
(
    id         BIGSERIAL PRIMARY KEY,
    uuid       UUID                     NOT NULL UNIQUE,
    name       VARCHAR(255)             NOT NULL,
    account_id BIGINT                   NOT NULL,
    CONSTRAINT fk_production_source_account
        FOREIGN KEY (account_id)
            REFERENCES account (id)
            ON DELETE CASCADE,
    current_kw NUMERIC(10, 2)           NOT NULL DEFAULT 0,
    peak_kw    NUMERIC(10, 2)           NOT NULL DEFAULT 0,
    api_type   VARCHAR(32)              NOT NULL CHECK (api_type IN ('SHELLY', 'SOFAR_SOLARMANPV')),
    app_id     TEXT,
    app_secret TEXT,
    email      TEXT,
    password   TEXT,
    station_id TEXT,
    enabled    BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_production_source_account
    ON production_source (account_id);

CREATE INDEX idx_production_source_uuid
    ON production_source (uuid);


CREATE TABLE production_history
(
    id                   BIGSERIAL PRIMARY KEY,
    production_source_id BIGINT                   NOT NULL,
    CONSTRAINT fk_production_history_source
        FOREIGN KEY (production_source_id)
            REFERENCES production_source (id)
            ON DELETE CASCADE,
    kw                   NUMERIC(10, 2)           NOT NULL,
    measured_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_production_history_source
    ON production_history (production_source_id);

CREATE INDEX idx_production_history_measured_at
    ON production_history (measured_at);
