-- "Forgot password?" requests waiting in the Admin queue (no email is sent).
CREATE TABLE password_reset_request (
    id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    requested_at DATETIME2    NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    handled_by   VARCHAR(100) NULL,
    handled_at   DATETIME2    NULL,
    CONSTRAINT fk_reset_request_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE INDEX ix_reset_request_status ON password_reset_request (status, requested_at);
