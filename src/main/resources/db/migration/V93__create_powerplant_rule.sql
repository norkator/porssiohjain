CREATE TABLE powerplant_rule
(
    id                     BIGSERIAL PRIMARY KEY,
    account_id             BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    source_element_id      BIGINT                   NOT NULL REFERENCES powerplant_element (id) ON DELETE CASCADE,
    target_element_id      BIGINT                   NOT NULL REFERENCES powerplant_element (id) ON DELETE CASCADE,
    comparison_type        VARCHAR(32)              NOT NULL,
    threshold_value        NUMERIC(10, 3)           NOT NULL,
    hysteresis_value       NUMERIC(10, 3),
    target_action          VARCHAR(32)              NOT NULL,
    enabled                BOOLEAN                  NOT NULL DEFAULT TRUE,
    cooldown_seconds       INTEGER                  NOT NULL DEFAULT 300,
    last_condition_matched BOOLEAN,
    last_command_sent_at   TIMESTAMP WITH TIME ZONE,
    last_evaluated_at      TIMESTAMP WITH TIME ZONE,
    last_skip_reason       VARCHAR(256),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_powerplant_rule_comparison CHECK (
        comparison_type IN ('LESS_THAN', 'LESS_THAN_OR_EQUAL', 'GREATER_THAN', 'GREATER_THAN_OR_EQUAL', 'EQUAL')
        ),
    CONSTRAINT chk_powerplant_rule_action CHECK (target_action IN ('TURN_ON', 'TURN_OFF')),
    CONSTRAINT chk_powerplant_rule_cooldown CHECK (cooldown_seconds >= 0),
    CONSTRAINT chk_powerplant_rule_not_self CHECK (source_element_id <> target_element_id)
);

CREATE INDEX idx_powerplant_rule_account ON powerplant_rule (account_id);
CREATE INDEX idx_powerplant_rule_source ON powerplant_rule (source_element_id);
CREATE INDEX idx_powerplant_rule_target ON powerplant_rule (target_element_id);
CREATE INDEX idx_powerplant_rule_enabled ON powerplant_rule (enabled) WHERE enabled;
