-- SQL Server only: the database itself refuses to change or remove audit rows.
CREATE TRIGGER trg_audit_log_append_only ON audit_log
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    THROW 51000, 'audit_log is append-only', 1;
END;
