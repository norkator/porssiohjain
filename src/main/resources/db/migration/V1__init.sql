CREATE TABLE nordpool
(
    id             BIGSERIAL PRIMARY KEY,
    delivery_start TIMESTAMPTZ    NOT NULL,
    delivery_end   TIMESTAMPTZ    NOT NULL,
    price_fi       NUMERIC(10, 4) NOT NULL,
    CONSTRAINT uk_delivery UNIQUE (delivery_start, delivery_end)
);

CREATE TABLE account
(
    id         BIGSERIAL PRIMARY KEY,
    uuid       UUID        NOT NULL UNIQUE,
    secret     VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);

CREATE TABLE device
(
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID         NOT NULL UNIQUE,
    device_name        VARCHAR(255) NOT NULL,
    last_communication TIMESTAMP DEFAULT NULL,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    account_id         BIGINT       NOT NULL,
    CONSTRAINT fk_device_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE TABLE control
(
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    max_price_snt INT          NOT NULL,
    account_id    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT fk_control_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE TABLE control_device
(
    id             BIGSERIAL PRIMARY KEY,
    control_id     BIGINT NOT NULL,
    device_id      BIGINT NOT NULL,
    device_channel INT    NOT NULL,
    CONSTRAINT fk_cd_control FOREIGN KEY (control_id) REFERENCES control (id) ON DELETE CASCADE,
    CONSTRAINT fk_cd_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT uq_control_device_channel UNIQUE (control_id, device_id, device_channel)
);

CREATE TABLE control_table
(
    id         BIGSERIAL PRIMARY KEY,
    control_id BIGINT         NOT NULL,
    start_time TIMESTAMP      NOT NULL,
    end_time   TIMESTAMP      NOT NULL,
    price_fi   NUMERIC(10, 4) NOT NULL,
    status     VARCHAR(10)    NOT NULL,
    created_at TIMESTAMP      NOT NULL,
    updated_at TIMESTAMP      NOT NULL,
    CONSTRAINT fk_ct_control FOREIGN KEY (control_id) REFERENCES control (id) ON DELETE CASCADE,
    CONSTRAINT uq_control_time UNIQUE (control_id, start_time, end_time),
    CONSTRAINT chk_status CHECK (status IN ('FINAL', 'MANUAL', 'PLANNED'))
);
