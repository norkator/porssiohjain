CREATE TABLE load_shedding_node
(
    id             BIGSERIAL PRIMARY KEY,
    account_id     BIGINT    NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    device_id      BIGINT    NOT NULL REFERENCES device (id) ON DELETE CASCADE,
    device_channel INTEGER   NOT NULL,
    canvas_x       INTEGER   NOT NULL DEFAULT 40,
    canvas_y       INTEGER   NOT NULL DEFAULT 40,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_load_shedding_node_account_device_channel UNIQUE (account_id, device_id, device_channel)
);

CREATE TABLE load_shedding_link
(
    id             BIGSERIAL PRIMARY KEY,
    account_id     BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    source_node_id BIGINT      NOT NULL REFERENCES load_shedding_node (id) ON DELETE CASCADE,
    target_node_id BIGINT      NOT NULL REFERENCES load_shedding_node (id) ON DELETE CASCADE,
    trigger_state  VARCHAR(32) NOT NULL,
    target_action  VARCHAR(32) NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_load_shedding_link UNIQUE (account_id, source_node_id, target_node_id, trigger_state, target_action)
);
