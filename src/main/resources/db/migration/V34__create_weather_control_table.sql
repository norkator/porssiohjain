CREATE TABLE weather_control
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255)             NOT NULL,
    account_id BIGINT                   NOT NULL,
    site_id    BIGINT                   NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_weather_control_account
        FOREIGN KEY (account_id)
            REFERENCES account (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_weather_control_site
        FOREIGN KEY (site_id)
            REFERENCES site (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_weather_control_account_id ON weather_control (account_id);
CREATE INDEX idx_weather_control_site_id ON weather_control (site_id);
