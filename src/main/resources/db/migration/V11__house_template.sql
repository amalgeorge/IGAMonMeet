-- Admin-uploaded House templates. Version 0 is the template bundled with the app (not stored here).
CREATE TABLE house_template (
    id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    version_no  INT            NOT NULL,
    file_name   NVARCHAR(255)  NOT NULL,
    content     ${longBinary}  NOT NULL,
    theme_json  ${longText}    NOT NULL,
    uploaded_by VARCHAR(100)   NOT NULL,
    uploaded_at DATETIME2      NOT NULL,
    CONSTRAINT uq_house_template_version UNIQUE (version_no)
);
