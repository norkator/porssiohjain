CREATE TABLE factory_device
(
    id                     BIGSERIAL PRIMARY KEY,
    serial_number          VARCHAR(128)             NOT NULL,
    hardware_mac           VARCHAR(64),
    chip_id                VARCHAR(128),
    platform               VARCHAR(32)              NOT NULL,
    product_model          VARCHAR(128)             NOT NULL,
    firmware_version       VARCHAR(128),
    mqtt_topic_root        VARCHAR(255)             NOT NULL,
    mqtt_username          VARCHAR(128)             NOT NULL,
    mqtt_password          TEXT                     NOT NULL,
    status                 VARCHAR(32)              NOT NULL,
    last_seen_at           TIMESTAMP WITH TIME ZONE,
    last_bootstrap_payload TEXT,
    metadata_json          TEXT,
    claimed_device_id      BIGINT,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_factory_device_serial_number UNIQUE (serial_number),
    CONSTRAINT uk_factory_device_hardware_mac UNIQUE (hardware_mac),
    CONSTRAINT uk_factory_device_mqtt_username UNIQUE (mqtt_username),
    CONSTRAINT uk_factory_device_mqtt_topic_root UNIQUE (mqtt_topic_root),
    CONSTRAINT fk_factory_device_claimed_device
        FOREIGN KEY (claimed_device_id) REFERENCES device (id) ON DELETE SET NULL
);

CREATE INDEX idx_factory_device_status ON factory_device (status);
CREATE INDEX idx_factory_device_last_seen_at ON factory_device (last_seen_at);

CREATE TABLE factory_test_run
(
    id                  BIGSERIAL PRIMARY KEY,
    factory_device_id   BIGINT                   NOT NULL,
    operator_account_id BIGINT,
    station_name        VARCHAR(128)             NOT NULL,
    status              VARCHAR(32)              NOT NULL,
    notes               TEXT,
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_factory_test_run_device
        FOREIGN KEY (factory_device_id) REFERENCES factory_device (id) ON DELETE CASCADE,
    CONSTRAINT fk_factory_test_run_operator
        FOREIGN KEY (operator_account_id) REFERENCES account (id) ON DELETE SET NULL
);

CREATE INDEX idx_factory_test_run_device ON factory_test_run (factory_device_id);
CREATE INDEX idx_factory_test_run_status ON factory_test_run (status);

CREATE TABLE factory_test_step_result
(
    id                  BIGSERIAL PRIMARY KEY,
    factory_test_run_id BIGINT                   NOT NULL,
    step_key            VARCHAR(128)             NOT NULL,
    status              VARCHAR(32)              NOT NULL,
    expected_value      TEXT,
    actual_value        TEXT,
    details             TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_factory_test_step_result_run
        FOREIGN KEY (factory_test_run_id) REFERENCES factory_test_run (id) ON DELETE CASCADE
);

CREATE INDEX idx_factory_test_step_result_run ON factory_test_step_result (factory_test_run_id);

CREATE TABLE ota_release
(
    id              BIGSERIAL PRIMARY KEY,
    platform        VARCHAR(32)              NOT NULL,
    product_model   VARCHAR(128)             NOT NULL,
    version         VARCHAR(128)             NOT NULL,
    binary_url      TEXT                     NOT NULL,
    checksum_sha256 VARCHAR(128),
    active          BOOLEAN                  NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ota_release_platform_model_version UNIQUE (platform, product_model, version)
);

CREATE INDEX idx_ota_release_lookup ON ota_release (platform, product_model, active);

CREATE TABLE ota_deployment
(
    id                      BIGSERIAL PRIMARY KEY,
    ota_release_id          BIGINT                   NOT NULL,
    factory_device_id       BIGINT,
    device_id               BIGINT,
    requested_by_account_id BIGINT,
    status                  VARCHAR(32)              NOT NULL,
    command_topic           VARCHAR(255)             NOT NULL,
    command_payload         TEXT                     NOT NULL,
    result_details          TEXT,
    requested_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ota_deployment_release
        FOREIGN KEY (ota_release_id) REFERENCES ota_release (id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_deployment_factory_device
        FOREIGN KEY (factory_device_id) REFERENCES factory_device (id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_deployment_device
        FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_deployment_requested_by
        FOREIGN KEY (requested_by_account_id) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT chk_ota_deployment_target
        CHECK ((factory_device_id IS NOT NULL) <> (device_id IS NOT NULL))
);

CREATE INDEX idx_ota_deployment_status ON ota_deployment (status);
