-- Comfort schedules keep the room minimum independent of the target.
-- Match configuration/simulation validation while retaining the maximum bound.
ALTER TABLE heating_planner_room
    DROP CONSTRAINT chk_heating_planner_room_limits;

ALTER TABLE heating_planner_room
    ADD CONSTRAINT chk_heating_planner_room_limits CHECK (
        minimum_room_temperature < maximum_room_temperature
            AND target_room_temperature <= maximum_room_temperature
        );
