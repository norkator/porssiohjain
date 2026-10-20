CREATE TABLE heating_planner_comfort_schedule (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    default_target NUMERIC(10, 2) NOT NULL CHECK (default_target BETWEEN 5 AND 35)
);
CREATE UNIQUE INDEX uk_heating_comfort_schedule_name
    ON heating_planner_comfort_schedule (account_id, site_id, name);

CREATE TABLE heating_planner_comfort_period (
    schedule_id BIGINT NOT NULL REFERENCES heating_planner_comfort_schedule (id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    time_from TIME NOT NULL,
    time_to TIME NOT NULL,
    target NUMERIC(10, 2) NOT NULL CHECK (target BETWEEN 5 AND 35),
    PRIMARY KEY (schedule_id, sort_order),
    CHECK (time_from <> time_to)
);

ALTER TABLE heating_planner_room ADD COLUMN comfort_schedule_id BIGINT
    REFERENCES heating_planner_comfort_schedule (id);
CREATE INDEX idx_heating_planner_room_comfort_schedule ON heating_planner_room (comfort_schedule_id);

ALTER TABLE heating_planner_plan_point ADD COLUMN comfort_target NUMERIC(10, 2);
