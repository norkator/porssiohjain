CREATE TABLE feature_request
(
    id                BIGSERIAL PRIMARY KEY,
    account_id        BIGINT                   NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    use_case          TEXT                     NOT NULL,
    requested_changes TEXT                     NOT NULL,
    contact_email     VARCHAR(254),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_feature_request_created ON feature_request (created_at DESC, id DESC);
