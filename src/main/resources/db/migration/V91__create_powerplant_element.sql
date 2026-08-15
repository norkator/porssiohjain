CREATE TABLE powerplant_element
(
    id             BIGSERIAL PRIMARY KEY,
    account_id     BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name           VARCHAR(128)             NOT NULL,
    element_type   VARCHAR(32)              NOT NULL,
    icon_name      VARCHAR(64)              NOT NULL,
    display_value  NUMERIC(10, 2),
    display_unit   VARCHAR(32),
    device_id      BIGINT                   REFERENCES device (id) ON DELETE SET NULL,
    device_channel INTEGER,
    canvas_x       INTEGER                  NOT NULL,
    canvas_y       INTEGER                  NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_powerplant_element_type CHECK (
        element_type IN ('INDICATOR', 'BUTTON', 'DEVICE_CONTROL', 'EQUIPMENT', 'LABEL')
        ),
    CONSTRAINT chk_powerplant_device_channel CHECK (
        device_channel IS NULL OR (device_channel >= 0 AND device_channel <= 3)
        )
);

CREATE INDEX idx_powerplant_element_account ON powerplant_element (account_id);
CREATE INDEX idx_powerplant_element_device ON powerplant_element (device_id) WHERE device_id IS NOT NULL;
