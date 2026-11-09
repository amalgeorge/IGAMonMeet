-- KPI library: one list of KPI definitions per Template version; Sections place KPIs from it (docs/spec-v2.md §2.1).
-- template_kpi rows stay as the placements, carrying a copy of the definition so existing reads are unchanged.
CREATE TABLE template_kpi_def (
    id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    template_version_id BIGINT         NOT NULL,
    kpi_key             VARCHAR(60)    NOT NULL,
    name                NVARCHAR(200)  NOT NULL,
    unit                NVARCHAR(20)   NOT NULL,
    target              DECIMAL(18,4)  NOT NULL,
    direction           VARCHAR(10)    NOT NULL,
    amber_threshold     DECIMAL(18,4)  NOT NULL,
    sort_order          INT            NOT NULL,
    CONSTRAINT fk_template_kpi_def_version FOREIGN KEY (template_version_id) REFERENCES template_version (id),
    CONSTRAINT uq_template_kpi_def_key UNIQUE (template_version_id, kpi_key)
);

-- Existing KPIs move into the library with their keys unchanged, so trend history carries on (Q21).
INSERT INTO template_kpi_def (template_version_id, kpi_key, name, unit, target, direction, amber_threshold, sort_order)
SELECT s.template_version_id, k.kpi_key, k.name, k.unit, k.target, k.direction, k.amber_threshold, s.sort_order * 100 + k.sort_order
FROM template_kpi k JOIN template_section s ON s.id = k.template_section_id;
