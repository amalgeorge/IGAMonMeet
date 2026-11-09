-- KPI value types (docs/spec-v2.md §2.2). Number by default; Text status options are a JSON list of {label, status}.
ALTER TABLE template_kpi_def ADD value_type VARCHAR(20) NOT NULL DEFAULT 'NUMBER';
ALTER TABLE template_kpi_def ADD currency VARCHAR(3) NULL;
ALTER TABLE template_kpi_def ADD status_options NVARCHAR(2000) NULL;
ALTER TABLE template_kpi ADD value_type VARCHAR(20) NOT NULL DEFAULT 'NUMBER';
ALTER TABLE template_kpi ADD currency VARCHAR(3) NULL;
ALTER TABLE template_kpi ADD status_options NVARCHAR(2000) NULL;
