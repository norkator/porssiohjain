CREATE TABLE device_ac_data
(
    id              BIGSERIAL PRIMARY KEY,
    device_id       BIGINT                   NOT NULL,
    name            VARCHAR(255)             NOT NULL,
    ac_type         VARCHAR(32)              NOT NULL DEFAULT 'NONE',
    ac_username     VARCHAR(255),
    ac_password     TEXT,
    ac_access_token TEXT,
    ac_consumer_id  VARCHAR(255),
    ac_device_id    VARCHAR(255),
    sas_token       TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_device_ac_data_device
        FOREIGN KEY (device_id)
            REFERENCES device (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_device_ac_data_device_ac_device_id
        UNIQUE (device_id, ac_device_id)
);

CREATE INDEX idx_device_ac_data_device_id ON device_ac_data (device_id);
CREATE INDEX idx_device_ac_data_ac_device_id ON device_ac_data (ac_device_id);
CREATE INDEX idx_device_ac_data_ac_consumer_id ON device_ac_data (ac_consumer_id);