CREATE TABLE zigbee_gateway_backup
(
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    gateway_id       UUID                     NOT NULL UNIQUE,
    coordinator_ieee VARCHAR(16)              NOT NULL,
    pan_id           INTEGER                  NOT NULL,
    extended_pan_id  VARCHAR(16)              NOT NULL,
    channel          INTEGER                  NOT NULL,
    devices_json     TEXT                     NOT NULL,
    backup_version   INTEGER                  NOT NULL DEFAULT 1,
    revision         BIGINT                   NOT NULL DEFAULT 1,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_zigbee_backup_account_coordinator UNIQUE (account_id, coordinator_ieee),
    CONSTRAINT ck_zigbee_backup_pan CHECK (pan_id BETWEEN 1 AND 65533),
    CONSTRAINT ck_zigbee_backup_channel CHECK (channel BETWEEN 11 AND 26)
);

CREATE INDEX idx_zigbee_gateway_backup_account ON zigbee_gateway_backup (account_id);
