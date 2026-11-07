-- Threaded Comments per Section: a root Comment and its replies; roots can be resolved.
CREATE TABLE meeting_comment (
    id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    meeting_id  BIGINT         NOT NULL,
    section_key VARCHAR(60)    NOT NULL,
    parent_id   BIGINT         NULL,
    author      VARCHAR(100)   NOT NULL,
    body        NVARCHAR(2000) NOT NULL,
    created_at  DATETIME2      NOT NULL,
    resolved    BIT            NOT NULL DEFAULT 0,
    resolved_by VARCHAR(100)   NULL,
    resolved_at DATETIME2      NULL,
    CONSTRAINT fk_comment_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES meeting_comment (id)
);
CREATE INDEX ix_comment_meeting ON meeting_comment (meeting_id, section_key);
