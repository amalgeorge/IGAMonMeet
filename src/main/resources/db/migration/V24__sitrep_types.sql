-- SITREP types offered in the SITREP table's Type column (docs/spec-v3.md §1.3), '|'-separated, edited in Admin Settings.
INSERT INTO app_setting (setting_key, setting_value) VALUES ('SITREP_TYPES', 'Incident|Problem|Change|Service request|Access request');
-- Set to 'true' once the default Template's Incidents Section has been replaced by SITREP (SitrepUpgrade).
INSERT INTO app_setting (setting_key, setting_value) VALUES ('UPGRADE_SITREP_DONE', 'false');
