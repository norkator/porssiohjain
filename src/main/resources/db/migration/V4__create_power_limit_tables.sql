CREATE TABLE power_limit
(
    id         BIGSERIAL PRIMARY KEY,
    timezone   VARCHAR(64)    NOT NULL DEFAULT 'UTC',
    uuid       UUID           NOT NULL UNIQUE,
    account_id BIGINT         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name       VARCHAR(255)   NOT NULL,
    limit_kw   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    current_kw NUMERIC(10, 2) NOT NULL DEFAULT 0,
    enabled    BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Table: power_limit_device
CREATE TABLE power_limit_device
(
    id             BIGSERIAL PRIMARY KEY,
    power_limit_id BIGINT NOT NULL REFERENCES power_limit (id) ON DELETE CASCADE,
    device_id      BIGINT NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    device_channel INT    NOT NULL,
    CONSTRAINT uq_power_limit_device UNIQUE (power_limit_id, device_id, device_channel)
);

-- Indexes for performance
CREATE INDEX idx_power_limit_account ON power_limit (account_id);
CREATE INDEX idx_power_limit_device_power_limit ON power_limit_device (power_limit_id);
CREATE INDEX idx_power_limit_device_device ON power_limit_device (device_id);
