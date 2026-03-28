CREATE TABLE site_weather
(
    id                   BIGSERIAL PRIMARY KEY,
    site_id              BIGINT                   NOT NULL,
    forecast_time        TIMESTAMP WITH TIME ZONE NOT NULL,
    temperature          NUMERIC(10, 2),
    wind_speed_ms        NUMERIC(10, 2),
    wind_gust            NUMERIC(10, 2),
    humidity             NUMERIC(10, 2),
    total_cloud_cover    NUMERIC(10, 2),
    precipitation_amount NUMERIC(10, 2),
    fetched_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_site_weather_site
        FOREIGN KEY (site_id)
            REFERENCES site (id)
            ON DELETE CASCADE,
    CONSTRAINT uk_site_weather_site_forecast_time
        UNIQUE (site_id, forecast_time)
);

CREATE INDEX idx_site_weather_site_id ON site_weather (site_id);
CREATE INDEX idx_site_weather_forecast_time ON site_weather (forecast_time);
