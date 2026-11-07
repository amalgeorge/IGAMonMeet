-- DBA break-glass and first-Admin setup (ADR 0002, docs/admin-recovery.md).
-- Creates the first Admin if none is active, or unlocks an existing active Admin,
-- then returns a one-time link valid for 1 hour. No password passes through the DBA.
CREATE PROCEDURE dbo.usp_AdminRecovery
    @username VARCHAR(100),
    @fullName NVARCHAR(200) = NULL,
    @email    VARCHAR(254)  = NULL,
    @baseUrl  VARCHAR(400)  = 'https://igamonmeet.example'
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @userId BIGINT, @status VARCHAR(20);
    DECLARE @now DATETIME2 = SYSUTCDATETIME();
    DECLARE @expires DATETIME2 = DATEADD(HOUR, 1, @now);

    BEGIN TRANSACTION;

    SELECT @userId = id, @status = status
    FROM app_user WITH (UPDLOCK, HOLDLOCK)
    WHERE UPPER(username) = UPPER(@username) AND role = 'ADMIN';

    IF @userId IS NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM app_user WITH (UPDLOCK, HOLDLOCK) WHERE role = 'ADMIN' AND status = 'ACTIVE')
            THROW 51001, 'No Admin has that username. Recovery works for an existing Admin, or creates the first Admin when none is active.', 1;
        IF EXISTS (SELECT 1 FROM app_user WHERE UPPER(username) = UPPER(@username))
            THROW 51002, 'That username belongs to a non-Admin user.', 1;

        INSERT INTO app_user (username, full_name, email, password_hash, role, status, failed_attempts, must_change_password, created_at, version)
        VALUES (@username, COALESCE(@fullName, @username), COALESCE(@email, ''), '!', 'ADMIN', 'ACTIVE', 0, 0, @now, 0);
        SET @userId = SCOPE_IDENTITY();
    END
    ELSE
    BEGIN
        IF @status <> 'ACTIVE'
            THROW 51003, 'That Admin account is not active. Another Admin must enable it.', 1;
        UPDATE app_user SET locked_until = NULL, failed_attempts = 0 WHERE id = @userId;
    END

    -- Any earlier unused token for this Admin stops working.
    UPDATE recovery_token SET used_at = @now WHERE user_id = @userId AND used_at IS NULL;

    DECLARE @token VARCHAR(64) = CONVERT(VARCHAR(64), CRYPT_GEN_RANDOM(32), 2);
    INSERT INTO recovery_token (user_id, token_hash, created_at, expires_at)
    VALUES (@userId, CONVERT(CHAR(64), HASHBYTES('SHA2_256', @token), 2), @now, @expires);

    INSERT INTO audit_log (occurred_at, actor, action, target_type, target_id, detail)
    VALUES (@now, LEFT(SUSER_SNAME(), 100), 'RECOVERY_ISSUED', 'User', CAST(@userId AS VARCHAR(100)), @username);

    COMMIT TRANSACTION;

    SELECT @baseUrl + '/recover#token=' + @token AS recovery_link, @expires AS expires_at_utc;
END;
