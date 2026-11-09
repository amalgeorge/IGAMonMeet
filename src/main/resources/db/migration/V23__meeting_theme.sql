-- Each Meeting uses one Theme (docs/spec-v2.md §4.2-4.3, ADR 0008). theme_version_no is set only when an Admin
-- pins a version on a Held Meeting; otherwise Preparing Meetings follow the latest version and Held ones the version
-- in force on the Meeting date. theme_id NULL (Meetings created before v2) means the default Theme.
-- No backfill here: SQL Server compiles the batch before the new columns exist.
ALTER TABLE meeting ADD theme_id BIGINT NULL;
ALTER TABLE meeting ADD theme_version_no INT NULL;
