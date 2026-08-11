CREATE TABLE heating_planner_wood_recommendation
(
    id                        BIGSERIAL PRIMARY KEY,
    settings_id               BIGINT                   NOT NULL REFERENCES heating_planner_settings (id) ON DELETE CASCADE,
    plan_id                   BIGINT                   NOT NULL REFERENCES heating_planner_plan (id) ON DELETE CASCADE,
    room_id                   BIGINT                   NOT NULL REFERENCES heating_planner_room (id) ON DELETE CASCADE,
    account_id                BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    site_id                   BIGINT                   NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    plan_version              UUID                     NOT NULL,
    load_name                 VARCHAR(128)             NOT NULL,
    wood_amount               NUMERIC(10, 2)           NOT NULL,
    notify_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    release_starts_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    release_ends_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    initial_room_heating_rate NUMERIC(10, 4)           NOT NULL,
    reason                    VARCHAR(1024)            NOT NULL,
    status                    VARCHAR(32)              NOT NULL DEFAULT 'PENDING',
    sent_at                   TIMESTAMP WITH TIME ZONE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_heating_planner_wood_recommendation_event UNIQUE (settings_id, release_starts_at),
    CONSTRAINT chk_heating_planner_wood_recommendation_status CHECK (
        status IN ('PENDING', 'SENT', 'SUPERSEDED', 'EXPIRED', 'LIT', 'SKIPPED')
    ),
    CONSTRAINT chk_heating_planner_wood_recommendation_times CHECK (
        notify_at <= release_starts_at AND release_starts_at < release_ends_at
    )
);

CREATE INDEX idx_heating_planner_wood_recommendation_due
    ON heating_planner_wood_recommendation (status, notify_at);
CREATE INDEX idx_heating_planner_wood_recommendation_plan
    ON heating_planner_wood_recommendation (plan_id);
