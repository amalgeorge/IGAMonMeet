-- Admin's slide layout for a Template Section (docs/spec-v3.md §6): item boxes on a 12 x 8 grid, as JSON. NULL = generated layout.
ALTER TABLE template_section ADD slide_layout NVARCHAR(4000) NULL;
