ALTER TABLE heating_planner_settings
    ADD COLUMN heat_pump_control_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE heating_planner_room_heat_source
    ADD COLUMN heat_pump_price_optimization_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN heat_pump_temperature_adjustment NUMERIC(4, 2) NOT NULL DEFAULT 2.00;

ALTER TABLE heating_planner_room_heat_source
DROP
CONSTRAINT chk_heating_planner_heat_source_type;

ALTER TABLE heating_planner_room_heat_source
    ADD CONSTRAINT chk_heating_planner_heat_source_type CHECK (
        source_type IN ('FLOOR_HEATING', 'WOOD_STOVE', 'HEAT_PUMP', 'HEAT_PUMP_OBSERVED_ONLY', 'OTHER')
        ),
    ADD CONSTRAINT chk_heating_planner_heat_pump_adjustment CHECK (
        heat_pump_temperature_adjustment >= 0 AND heat_pump_temperature_adjustment <= 5
    );

ALTER TABLE heating_planner_plan_point
    ADD COLUMN planned_heat_pump_setpoint NUMERIC(10, 2);
