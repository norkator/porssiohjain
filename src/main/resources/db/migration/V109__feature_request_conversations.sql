ALTER TABLE feature_request ADD COLUMN handled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE feature_request ADD COLUMN latest_admin_comment_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE feature_request ADD COLUMN user_read_comment_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_feature_request_owner ON feature_request (account_id, created_at DESC, id DESC);
CREATE INDEX idx_feature_request_handled ON feature_request (handled, created_at DESC, id DESC);

CREATE TABLE feature_request_comment (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES feature_request (id) ON DELETE CASCADE,
    account_id BIGINT REFERENCES account (id) ON DELETE SET NULL,
    admin_author BOOLEAN NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_feature_request_comment_request ON feature_request_comment (request_id, id);
