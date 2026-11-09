-- Named Versions of a Meeting: taken on import, restore, the meeting-date snapshot ("as presented") and by hand.
CREATE TABLE meeting_version (
    id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    meeting_id    BIGINT        NOT NULL,
    version_no    INT           NOT NULL,
    reason        VARCHAR(20)   NOT NULL,
    label         NVARCHAR(200) NOT NULL,
    snapshot_json ${longText}   NOT NULL,
    created_by    VARCHAR(100)  NOT NULL,
    created_at    DATETIME2     NOT NULL,
    CONSTRAINT fk_meeting_version_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON DELETE CASCADE,
    CONSTRAINT uq_meeting_version UNIQUE (meeting_id, version_no)
);
