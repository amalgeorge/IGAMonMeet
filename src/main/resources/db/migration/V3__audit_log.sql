CREATE TABLE audit_log (
    id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    occurred_at DATETIME2      NOT NULL,
    actor       VARCHAR(100)   NULL,
    action      VARCHAR(50)    NOT NULL,
    target_type VARCHAR(50)    NULL,
    target_id   VARCHAR(100)   NULL,
    field       VARCHAR(100)   NULL,
    old_value   NVARCHAR(4000) NULL,
    new_value   NVARCHAR(4000) NULL,
    detail      NVARCHAR(1000) NULL
);
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_actor ON audit_log (actor, occurred_at);
