-- Section groups: an optional heading over several Sections (docs/spec-v2.md §1.5).
CREATE TABLE template_group (
    id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    template_version_id BIGINT        NOT NULL,
    group_key           VARCHAR(60)   NOT NULL,
    name                NVARCHAR(200) NOT NULL,
    visibility          VARCHAR(10)   NOT NULL DEFAULT 'SHOWN',
    divider             BIT           NOT NULL DEFAULT 1,
    sort_order          INT           NOT NULL,
    CONSTRAINT fk_template_group_version FOREIGN KEY (template_version_id) REFERENCES template_version (id),
    CONSTRAINT uq_template_group_key UNIQUE (template_version_id, group_key)
);

ALTER TABLE template_section ADD group_key VARCHAR(60) NULL;
