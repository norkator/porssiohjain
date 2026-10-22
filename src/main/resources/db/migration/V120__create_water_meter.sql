CREATE TABLE water_meter
(
    id                        BIGSERIAL PRIMARY KEY,
    account_id                BIGINT       NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name                      VARCHAR(255) NOT NULL,
    portal_url                VARCHAR(255) NOT NULL,
    payer_number              TEXT         NOT NULL,
    water_meter_number        TEXT         NOT NULL,
    enabled                   BOOLEAN      NOT NULL DEFAULT TRUE,
    latest_total_cubic_meters NUMERIC(20, 6),
    observed_at               TIMESTAMP WITH TIME ZONE,
    last_poll_attempt_at      TIMESTAMP WITH TIME ZONE,
    last_error_key            VARCHAR(100)
);

CREATE INDEX idx_water_meter_account ON water_meter (account_id);
