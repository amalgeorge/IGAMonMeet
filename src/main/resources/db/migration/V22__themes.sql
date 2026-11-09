-- Themes: named colours, fonts, backgrounds and logo over the House template (docs/spec-v2.md §4, ADR 0008).
CREATE TABLE theme (
    id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL,
    is_default  BIT           NOT NULL DEFAULT 0,
    created_by  VARCHAR(100)  NOT NULL,
    created_at  DATETIME2     NOT NULL,
    CONSTRAINT uq_theme_name UNIQUE (name)
);

-- Every save is a new version; Meetings point at versions (ticket 31).
CREATE TABLE theme_version (
    id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    theme_id      BIGINT        NOT NULL,
    version_no    INT           NOT NULL,
    settings_json ${longText}   NOT NULL,
    created_by    VARCHAR(100)  NOT NULL,
    created_at    DATETIME2     NOT NULL,
    CONSTRAINT fk_theme_version_theme FOREIGN KEY (theme_id) REFERENCES theme (id),
    CONSTRAINT uq_theme_version UNIQUE (theme_id, version_no)
);

-- Backgrounds (cropped to 16:9) and logos. Never changed once stored.
CREATE TABLE theme_image (
    id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    content_type VARCHAR(30)   NOT NULL,
    content      ${longBinary} NOT NULL,
    width        INT           NOT NULL,
    height       INT           NOT NULL,
    uploaded_by  VARCHAR(100)  NOT NULL,
    uploaded_at  DATETIME2     NOT NULL
);

INSERT INTO theme (name, is_default, created_by, created_at) VALUES ('IAM Red', 1, 'system', CURRENT_TIMESTAMP);
INSERT INTO theme_version (theme_id, version_no, settings_json, created_by, created_at)
SELECT id, 1, '{"colors":{"primary":"C8102E","accent":"ED1B2E","text":"1A1A1A","background":"FFFFFF","onTarget":"1E7D43","belowTarget":"A35D00","offTarget":"C8102E"},"fonts":{"heading":"Arial","body":"Arial"},"backgrounds":{"title":{"color":"C8102E"},"divider":{"color":"C8102E"},"content":{"color":"FFFFFF"}},"logo":{"show":true}}',
       'system', CURRENT_TIMESTAMP
FROM theme WHERE name = 'IAM Red';
