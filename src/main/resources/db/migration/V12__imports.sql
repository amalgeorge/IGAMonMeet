-- Uploaded PowerPoint files and what was done with them. The original is kept for audit.
CREATE TABLE import_record (
    id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    meeting_id  BIGINT         NOT NULL,
    file_name   NVARCHAR(255)  NOT NULL,
    content     ${longBinary}  NOT NULL,
    slide_count INT            NOT NULL,
    uploaded_by VARCHAR(100)   NOT NULL,
    uploaded_at DATETIME2      NOT NULL,
    applied_by  VARCHAR(100)   NULL,
    applied_at  DATETIME2      NULL,
    result      NVARCHAR(2000) NULL,
    CONSTRAINT fk_import_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON DELETE CASCADE
);
