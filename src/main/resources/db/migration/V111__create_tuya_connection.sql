CREATE TABLE tuya_connection
(
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT NOT NULL UNIQUE REFERENCES account (id) ON DELETE CASCADE,
    provider_user_id  VARCHAR(128),
    account_label    VARCHAR(255),
    session_data     TEXT
);

CREATE TABLE tuya_device
(
    id               BIGSERIAL PRIMARY KEY,
    device_id        BIGINT NOT NULL UNIQUE REFERENCES device (id) ON DELETE CASCADE,
    connection_id    BIGINT NOT NULL REFERENCES tuya_connection (id) ON DELETE CASCADE,
    tuya_device_id   VARCHAR(128) NOT NULL,
    UNIQUE (connection_id, tuya_device_id)
);
