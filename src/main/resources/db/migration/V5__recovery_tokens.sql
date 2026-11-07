-- One-time Admin recovery tokens issued by dbo.usp_AdminRecovery. Only the SHA2-256 hash (upper-case hex) is stored.
CREATE TABLE recovery_token (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    token_hash CHAR(64)  NOT NULL,
    created_at DATETIME2 NOT NULL,
    expires_at DATETIME2 NOT NULL,
    used_at    DATETIME2 NULL,
    CONSTRAINT fk_recovery_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_recovery_token_hash UNIQUE (token_hash)
);
