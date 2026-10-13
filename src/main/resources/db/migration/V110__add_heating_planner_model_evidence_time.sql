ALTER TABLE heating_planner_room
    ADD COLUMN model_evidence_at TIMESTAMP WITH TIME ZONE;

-- Legacy models have no observation timestamp. Bound their transition period by
-- their existing training time; the next successful rebuild records actual evidence.
UPDATE heating_planner_room
SET model_evidence_at = model_trained_at
WHERE model_parameters_learned = TRUE
  AND model_confidence >= 0.2500;
