-- KPI tiles per slide per KPI Section: '2', '3', '4', '6', '8' or 'TABLE'; NULL means the default 4 (docs/spec-v2.md §2.5).
ALTER TABLE template_section ADD kpi_layout VARCHAR(10) NULL;
