CREATE TABLE meeting (
    id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    meeting_month       CHAR(7)       NOT NULL,  -- YYYY-MM
    meeting_date        DATE          NOT NULL,  -- Singapore calendar day
    title               NVARCHAR(200) NOT NULL,
    template_version_no INT           NOT NULL,
    created_by          VARCHAR(100)  NOT NULL,
    created_at          DATETIME2     NOT NULL,
    updated_at          DATETIME2     NOT NULL,
    deleted_at          DATETIME2     NULL,
    deleted_by          VARCHAR(100)  NULL,
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_meeting_date ON meeting (meeting_date);

-- A Meeting's copy of its Template version's Sections, plus the content written for this month.
CREATE TABLE meeting_section (
    id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    meeting_id    BIGINT         NOT NULL,
    section_key   VARCHAR(60)    NOT NULL,
    name          NVARCHAR(200)  NOT NULL,
    team_tag      VARCHAR(20)    NOT NULL,
    kind          VARCHAR(20)    NOT NULL,
    layout        VARCHAR(40)    NOT NULL,
    overflow_rule VARCHAR(20)    NOT NULL,
    required      BIT            NOT NULL,
    columns_spec  NVARCHAR(1000) NULL,
    chart_kpi_key VARCHAR(60)    NULL,
    sort_order    INT            NOT NULL,
    content_json  ${longText}    NULL,
    updated_by    VARCHAR(100)   NULL,
    updated_at    DATETIME2      NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_meeting_section_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON DELETE CASCADE,
    CONSTRAINT uq_meeting_section_key UNIQUE (meeting_id, section_key)
);

CREATE TABLE action_item (
    id                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    meeting_id        BIGINT         NOT NULL,
    title             NVARCHAR(500)  NOT NULL,
    owner             NVARCHAR(200)  NOT NULL,
    team_tag          VARCHAR(20)    NOT NULL,
    due_date          DATE           NULL,
    status            VARCHAR(10)    NOT NULL,
    origin_meeting_id BIGINT         NULL,
    sort_order        INT            NOT NULL,
    created_at        DATETIME2      NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_action_item_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id) ON DELETE CASCADE
);
CREATE INDEX ix_action_item_meeting ON action_item (meeting_id, status);
