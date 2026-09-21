CREATE TABLE service_notice
(
    id           INTEGER                  PRIMARY KEY DEFAULT 1,
    active       BOOLEAN                  NOT NULL DEFAULT FALSE,
    finnish_text TEXT                     NOT NULL DEFAULT '',
    english_text TEXT                     NOT NULL DEFAULT '',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_service_notice_singleton CHECK (id = 1)
);
