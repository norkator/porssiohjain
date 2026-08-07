CREATE TABLE heating_planner_settings
(
    id                                    BIGSERIAL PRIMARY KEY,
    account_id                            BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    site_id                               BIGINT                   NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    enabled                               BOOLEAN                  NOT NULL DEFAULT FALSE,
    active_control_enabled                BOOLEAN                  NOT NULL DEFAULT FALSE,
    timezone                              VARCHAR(64)              NOT NULL DEFAULT 'Europe/Helsinki',
    planner_active_below_temperature      NUMERIC(10, 2)           NOT NULL DEFAULT 5.00,
    wood_recommendation_below_temperature NUMERIC(10, 2)           NOT NULL DEFAULT 0.00,
    cheap_price_threshold                 NUMERIC(10, 4)           NOT NULL DEFAULT 5.0000,
    expensive_price_threshold             NUMERIC(10, 4)           NOT NULL DEFAULT 20.0000,
    preheat_look_ahead_minutes            INTEGER                  NOT NULL DEFAULT 360,
    simulation_step_minutes               INTEGER                  NOT NULL DEFAULT 15,
    model_version                         VARCHAR(64)              NOT NULL DEFAULT 'deterministic-v1',
    created_at                            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_heating_planner_settings_account_site UNIQUE (account_id, site_id),
    CONSTRAINT chk_heating_planner_preheat_look_ahead_positive CHECK (preheat_look_ahead_minutes > 0),
    CONSTRAINT chk_heating_planner_simulation_step_positive CHECK (simulation_step_minutes > 0)
);

CREATE INDEX idx_heating_planner_settings_account ON heating_planner_settings (account_id);
CREATE INDEX idx_heating_planner_settings_site ON heating_planner_settings (site_id);

CREATE TABLE heating_planner_room
(
    id                                 BIGSERIAL PRIMARY KEY,
    settings_id                        BIGINT                   NOT NULL REFERENCES heating_planner_settings (id) ON DELETE CASCADE,
    account_id                         BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    site_id                            BIGINT                   NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    name                               VARCHAR(128)             NOT NULL,
    enabled                            BOOLEAN                  NOT NULL DEFAULT TRUE,
    room_sensor_device_id              BIGINT                   REFERENCES device (id) ON DELETE SET NULL,
    room_sensor_measurement_key        VARCHAR(128),
    floor_sensor_device_id             BIGINT                   REFERENCES device (id) ON DELETE SET NULL,
    floor_sensor_measurement_key       VARCHAR(128),
    normal_floor_temperature           NUMERIC(10, 2)           NOT NULL DEFAULT 23.00,
    maximum_preheat_floor_temperature  NUMERIC(10, 2)           NOT NULL DEFAULT 27.00,
    absolute_maximum_floor_temperature NUMERIC(10, 2)           NOT NULL DEFAULT 29.00,
    discharge_floor_setpoint           NUMERIC(10, 2)           NOT NULL DEFAULT 19.00,
    minimum_room_temperature           NUMERIC(10, 2)           NOT NULL DEFAULT 20.00,
    target_room_temperature            NUMERIC(10, 2)           NOT NULL DEFAULT 21.00,
    maximum_room_temperature           NUMERIC(10, 2)           NOT NULL DEFAULT 23.50,
    heater_power_kw                    NUMERIC(10, 3),
    floor_heating_rate                 NUMERIC(10, 4),
    floor_to_room_rate                 NUMERIC(10, 4),
    room_outdoor_loss_rate             NUMERIC(10, 4),
    wind_loss_rate                     NUMERIC(10, 4),
    model_parameters_learned           BOOLEAN                  NOT NULL DEFAULT FALSE,
    sort_order                         INTEGER                  NOT NULL DEFAULT 0,
    created_at                         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_heating_planner_room_settings_name UNIQUE (settings_id, name),
    CONSTRAINT chk_heating_planner_floor_limits CHECK (
        normal_floor_temperature <= maximum_preheat_floor_temperature
            AND maximum_preheat_floor_temperature <= absolute_maximum_floor_temperature
        ),
    CONSTRAINT chk_heating_planner_room_limits CHECK (
        minimum_room_temperature < target_room_temperature
            AND target_room_temperature <= maximum_room_temperature
        )
);

CREATE INDEX idx_heating_planner_room_settings ON heating_planner_room (settings_id);
CREATE INDEX idx_heating_planner_room_account_site ON heating_planner_room (account_id, site_id);
CREATE INDEX idx_heating_planner_room_room_sensor ON heating_planner_room (room_sensor_device_id) WHERE room_sensor_device_id IS NOT NULL;
CREATE INDEX idx_heating_planner_room_floor_sensor ON heating_planner_room (floor_sensor_device_id) WHERE floor_sensor_device_id IS NOT NULL;

CREATE TABLE heating_planner_room_heat_source
(
    id                    BIGSERIAL PRIMARY KEY,
    room_id               BIGINT                   NOT NULL REFERENCES heating_planner_room (id) ON DELETE CASCADE,
    account_id            BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    site_id               BIGINT                   NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    name                  VARCHAR(128)             NOT NULL,
    source_type           VARCHAR(32)              NOT NULL,
    useful_in_calculation BOOLEAN                  NOT NULL DEFAULT TRUE,
    controlling_device_id BIGINT                   REFERENCES device (id) ON DELETE SET NULL,
    thermostat_channel    INTEGER,
    estimated_power_kw    NUMERIC(10, 3),
    heat_share            NUMERIC(10, 4),
    enabled               BOOLEAN                  NOT NULL DEFAULT TRUE,
    sort_order            INTEGER                  NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_heating_planner_heat_source_type CHECK (
        source_type IN ('FLOOR_HEATING', 'WOOD_STOVE', 'HEAT_PUMP_OBSERVED_ONLY', 'OTHER')
        )
);

CREATE INDEX idx_heating_planner_heat_source_room ON heating_planner_room_heat_source (room_id);
CREATE INDEX idx_heating_planner_heat_source_account_site ON heating_planner_room_heat_source (account_id, site_id);
CREATE INDEX idx_heating_planner_heat_source_device ON heating_planner_room_heat_source (controlling_device_id) WHERE controlling_device_id IS NOT NULL;

CREATE TABLE heating_planner_plan
(
    id             BIGSERIAL PRIMARY KEY,
    settings_id    BIGINT                   NOT NULL REFERENCES heating_planner_settings (id) ON DELETE CASCADE,
    account_id     BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    site_id        BIGINT                   NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    plan_version   UUID                     NOT NULL UNIQUE,
    horizon_start  TIMESTAMP WITH TIME ZONE NOT NULL,
    horizon_end    TIMESTAMP WITH TIME ZONE NOT NULL,
    trigger_reason VARCHAR(128)             NOT NULL,
    status         VARCHAR(32)              NOT NULL DEFAULT 'SIMULATED',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    superseded_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_heating_planner_plan_status CHECK (
        status IN ('SIMULATED', 'ACTIVE', 'SUPERSEDED')
        ),
    CONSTRAINT chk_heating_planner_plan_horizon CHECK (horizon_start < horizon_end)
);

CREATE INDEX idx_heating_planner_plan_settings_status ON heating_planner_plan (settings_id, status);
CREATE INDEX idx_heating_planner_plan_account_site_created ON heating_planner_plan (account_id, site_id, created_at DESC);

CREATE TABLE heating_planner_plan_point
(
    id                          BIGSERIAL PRIMARY KEY,
    plan_id                     BIGINT                   NOT NULL REFERENCES heating_planner_plan (id) ON DELETE CASCADE,
    room_id                     BIGINT                   NOT NULL REFERENCES heating_planner_room (id) ON DELETE CASCADE,
    account_id                  BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    site_id                     BIGINT                   NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    plan_version                UUID                     NOT NULL,
    planned_time                TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    price_cents_per_kwh         NUMERIC(10, 4),
    outdoor_temperature         NUMERIC(10, 2),
    wind_speed_ms               NUMERIC(10, 2),
    predicted_floor_temperature NUMERIC(10, 2),
    predicted_room_temperature  NUMERIC(10, 2),
    planned_floor_setpoint      NUMERIC(10, 2),
    predicted_wood_heat_rate    NUMERIC(10, 4),
    heating                     BOOLEAN                  NOT NULL DEFAULT FALSE,
    operating_mode              VARCHAR(32)              NOT NULL,
    reason                      VARCHAR(1024)            NOT NULL,
    status                      VARCHAR(32)              NOT NULL DEFAULT 'SIMULATED',
    CONSTRAINT uk_heating_planner_plan_point_version_room_time UNIQUE (plan_version, room_id, planned_time),
    CONSTRAINT chk_heating_planner_plan_point_status CHECK (
        status IN ('SIMULATED', 'ACTIVE', 'SUPERSEDED', 'COMPLETED')
        ),
    CONSTRAINT chk_heating_planner_plan_point_mode CHECK (
        operating_mode IN ('NORMAL', 'INACTIVE', 'PREHEAT', 'DISCHARGE', 'COMFORT_RECOVERY')
        )
);

CREATE INDEX idx_heating_planner_plan_point_plan ON heating_planner_plan_point (plan_id);
CREATE INDEX idx_heating_planner_plan_point_room_time ON heating_planner_plan_point (room_id, planned_time);
CREATE INDEX idx_heating_planner_plan_point_account_site_time ON heating_planner_plan_point (account_id, site_id, planned_time);
CREATE INDEX idx_heating_planner_plan_point_version ON heating_planner_plan_point (plan_version);
