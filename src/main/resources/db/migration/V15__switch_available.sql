-- Available Sections and KPIs switched on per Meeting (docs/spec-v2.md §1.3, ADR 0008).
-- template_version_no: the Template version this Section's definition comes from (NULL = the Meeting's own version).
ALTER TABLE meeting_section ADD template_version_no INT NULL;
ALTER TABLE meeting_section ADD switched_on BIT NOT NULL DEFAULT 0;
ALTER TABLE meeting_section ADD hidden BIT NOT NULL DEFAULT 0;
ALTER TABLE meeting_section ADD enabled_kpis NVARCHAR(1000) NULL;
