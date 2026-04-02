CREATE TABLE device_ac_command_log
(
    id         BIGSERIAL PRIMARY KEY,
    device_id  BIGINT                   NOT NULL,
    sent_data  TEXT                     NOT NULL,
    sent_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_device_ac_command_log_device
        FOREIGN KEY (device_id)
            REFERENCES device (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_device_ac_command_log_device_id ON device_ac_command_log (device_id);
CREATE INDEX idx_device_ac_command_log_device_sent_at ON device_ac_command_log (device_id, sent_at DESC, id DESC);
