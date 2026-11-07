-- Admin-controlled feature toggles (docs/spec.md §1.11).
CREATE TABLE app_setting (
    setting_key   VARCHAR(100) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(400) NOT NULL,
    updated_by    VARCHAR(100) NULL,
    updated_at    DATETIME2    NULL
);
INSERT INTO app_setting (setting_key, setting_value) VALUES ('SELF_REGISTRATION', 'true');
INSERT INTO app_setting (setting_key, setting_value) VALUES ('REGISTRATION_APPROVAL', 'true');
INSERT INTO app_setting (setting_key, setting_value) VALUES ('FORGOT_PASSWORD', 'true');
INSERT INTO app_setting (setting_key, setting_value) VALUES ('QUEUE_BADGE', 'true');
