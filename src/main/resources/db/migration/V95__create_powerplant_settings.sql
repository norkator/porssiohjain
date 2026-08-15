CREATE TABLE powerplant_settings
(
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    board_width  INTEGER                  NOT NULL DEFAULT 1600,
    board_height INTEGER                  NOT NULL DEFAULT 900,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_powerplant_settings_account UNIQUE (account_id),
    CONSTRAINT chk_powerplant_board_width CHECK (board_width >= 800 AND board_width <= 4000),
    CONSTRAINT chk_powerplant_board_height CHECK (board_height >= 500 AND board_height <= 2400)
);

CREATE INDEX idx_powerplant_settings_account ON powerplant_settings (account_id);
