-- Visibility of Template Sections and KPIs, and Sections kept off the slides (docs/spec-v2.md §1.1, §1.2).
ALTER TABLE template_section ADD visibility VARCHAR(10) NOT NULL DEFAULT 'SHOWN';
ALTER TABLE template_section ADD on_slides BIT NOT NULL DEFAULT 1;
ALTER TABLE template_kpi ADD visibility VARCHAR(10) NOT NULL DEFAULT 'SHOWN';
ALTER TABLE meeting_section ADD on_slides BIT NOT NULL DEFAULT 1;
