-- Template versions are immutable; editing publishes a new version (docs/spec.md §2).
CREATE TABLE template_version (
    id         BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    version_no INT            NOT NULL,
    created_by VARCHAR(100)   NOT NULL,
    created_at DATETIME2      NOT NULL,
    note       NVARCHAR(500)  NULL,
    CONSTRAINT uq_template_version_no UNIQUE (version_no)
);

CREATE TABLE template_section (
    id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    template_version_id BIGINT         NOT NULL,
    section_key         VARCHAR(60)    NOT NULL,
    name                NVARCHAR(200)  NOT NULL,
    team_tag            VARCHAR(20)    NOT NULL,
    kind                VARCHAR(20)    NOT NULL,
    layout              VARCHAR(40)    NOT NULL,
    overflow_rule       VARCHAR(20)    NOT NULL,
    required            BIT            NOT NULL,
    columns_spec        NVARCHAR(1000) NULL,
    chart_kpi_key       VARCHAR(60)    NULL,
    sort_order            INT            NOT NULL,
    CONSTRAINT fk_template_section_version FOREIGN KEY (template_version_id) REFERENCES template_version (id),
    CONSTRAINT uq_template_section_key UNIQUE (template_version_id, section_key)
);

CREATE TABLE template_kpi (
    id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    template_section_id BIGINT         NOT NULL,
    kpi_key             VARCHAR(60)    NOT NULL,
    name                NVARCHAR(200)  NOT NULL,
    unit                NVARCHAR(20)   NOT NULL,
    target              DECIMAL(18,4)  NOT NULL,
    direction           VARCHAR(10)    NOT NULL,
    amber_threshold     DECIMAL(18,4)  NOT NULL,
    required            BIT            NOT NULL,
    sort_order            INT            NOT NULL,
    CONSTRAINT fk_template_kpi_section FOREIGN KEY (template_section_id) REFERENCES template_section (id)
);

-- Version 1: the Sections and KPIs from the design mockup.
INSERT INTO template_version (version_no, created_by, created_at, note) VALUES (1, 'system', '2026-11-07 00:00:00', 'Initial template');

INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'summary', 'Executive summary', 'SHARED', 'SUMMARY', 'IAM_SUMMARY', 'SHRINK_TO_FIT', 1, NULL, NULL, 1 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'kpis', 'Key metrics', 'OPERATIONS', 'KPI', 'IAM_KPI', 'CONTINUE', 1, NULL, NULL, 2 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'sla-trend', 'Access requests within SLA', 'OPERATIONS', 'CHART', 'IAM_CHART', 'CONTINUE', 0, NULL, 'sla', 3 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'incidents', 'Incidents', 'OPERATIONS', 'TABLE', 'IAM_TABLE', 'CONTINUE', 1, 'Incident|Impact|Status|Resolved', NULL, 4 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'access-reviews', 'Access reviews', 'OPERATIONS', 'TABLE', 'IAM_TABLE', 'CONTINUE', 0, 'Campaign|Business unit|Complete|Due', NULL, 5 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'roadmap', 'Roadmap', 'ENGINEERING', 'TABLE', 'IAM_TABLE', 'CONTINUE', 1, 'Initiative|Owner|Status|Due', NULL, 6 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'releases', 'Releases', 'ENGINEERING', 'TABLE', 'IAM_TABLE', 'CONTINUE', 0, 'Release|Date|Scope', NULL, 7 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'risks', 'Risks and decisions', 'SHARED', 'TWO_COLUMN', 'IAM_TWO_COLUMN', 'SHRINK_TO_FIT', 1, NULL, NULL, 8 FROM template_version WHERE version_no = 1;
INSERT INTO template_section (template_version_id, section_key, name, team_tag, kind, layout, overflow_rule, required, columns_spec, chart_kpi_key, sort_order)
SELECT id, 'actions', 'Action items', 'SHARED', 'ACTION_ITEMS', 'IAM_TABLE', 'CONTINUE', 1, NULL, NULL, 9 FROM template_version WHERE version_no = 1;

INSERT INTO template_kpi (template_section_id, kpi_key, name, unit, target, direction, amber_threshold, required, sort_order)
SELECT s.id, 'sla', 'Access requests within SLA', '%', 95, 'HIGHER', 90, 1, 1 FROM template_section s JOIN template_version v ON v.id = s.template_version_id WHERE v.version_no = 1 AND s.section_key = 'kpis';
INSERT INTO template_kpi (template_section_id, kpi_key, name, unit, target, direction, amber_threshold, required, sort_order)
SELECT s.id, 'provisioning', 'Mean provisioning time', 'h', 4, 'LOWER', 6, 1, 2 FROM template_section s JOIN template_version v ON v.id = s.template_version_id WHERE v.version_no = 1 AND s.section_key = 'kpis';
INSERT INTO template_kpi (template_section_id, kpi_key, name, unit, target, direction, amber_threshold, required, sort_order)
SELECT s.id, 'leaver', 'Leaver access removed in 24 h', '%', 100, 'HIGHER', 99, 1, 3 FROM template_section s JOIN template_version v ON v.id = s.template_version_id WHERE v.version_no = 1 AND s.section_key = 'kpis';
INSERT INTO template_kpi (template_section_id, kpi_key, name, unit, target, direction, amber_threshold, required, sort_order)
SELECT s.id, 'certification', 'Certification campaign complete', '%', 90, 'HIGHER', 75, 0, 4 FROM template_section s JOIN template_version v ON v.id = s.template_version_id WHERE v.version_no = 1 AND s.section_key = 'kpis';
