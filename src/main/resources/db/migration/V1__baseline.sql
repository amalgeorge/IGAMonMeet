-- Baseline. Real tables arrive with the feature tickets (accounts first).
CREATE TABLE app_info (
    id INT NOT NULL PRIMARY KEY,
    schema_note VARCHAR(200) NOT NULL
);
INSERT INTO app_info (id, schema_note) VALUES (1, 'IAM Monthly Meeting');
