-- KPI display fields and global status labels (docs/spec-v2.md §2.3, §2.4).
ALTER TABLE template_kpi_def ADD subtitle NVARCHAR(200) NULL;
ALTER TABLE template_kpi_def ADD description NVARCHAR(1000) NULL;
ALTER TABLE template_kpi_def ADD decimals INT NULL;
ALTER TABLE template_kpi ADD subtitle NVARCHAR(200) NULL;
ALTER TABLE template_kpi ADD description NVARCHAR(1000) NULL;
ALTER TABLE template_kpi ADD decimals INT NULL;

INSERT INTO app_setting (setting_key, setting_value) VALUES ('STATUS_LABEL_ON_TARGET', 'On target');
INSERT INTO app_setting (setting_key, setting_value) VALUES ('STATUS_LABEL_BELOW_TARGET', 'Below target');
INSERT INTO app_setting (setting_key, setting_value) VALUES ('STATUS_LABEL_OFF_TARGET', 'Off target');
