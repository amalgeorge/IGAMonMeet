CREATE TABLE app_user (
    id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    username             VARCHAR(100)  NOT NULL,
    full_name            NVARCHAR(200) NOT NULL,
    email                VARCHAR(254)  NOT NULL,
    password_hash        VARCHAR(100)  NOT NULL,
    role                 VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    failed_attempts      INT           NOT NULL DEFAULT 0,
    locked_until         DATETIME2     NULL,
    must_change_password BIT           NOT NULL DEFAULT 0,
    created_at           DATETIME2     NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_user_username UNIQUE (username)
);

-- Every password a user has had, newest last; the last 5 cannot be reused.
CREATE TABLE password_history (
    id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME2    NOT NULL,
    CONSTRAINT fk_password_history_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE INDEX ix_password_history_user ON password_history (user_id, created_at);
