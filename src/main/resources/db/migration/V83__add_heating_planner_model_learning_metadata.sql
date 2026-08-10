ALTER TABLE heating_planner_room
    ADD COLUMN model_sample_count INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN model_confidence   NUMERIC(5, 4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN model_trained_at   TIMESTAMP WITH TIME ZONE;

ALTER TABLE heating_planner_room
    ADD CONSTRAINT chk_heating_planner_model_sample_count CHECK (model_sample_count >= 0),
    ADD CONSTRAINT chk_heating_planner_model_confidence CHECK (model_confidence >= 0 AND model_confidence <= 1);
