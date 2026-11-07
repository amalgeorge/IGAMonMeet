# PPTX import accepts .pptx and .potx only; layout names are the contract

Import uses Apache POI (XSLF) and accepts only `.pptx` and `.potx` up to 50 MB. Legacy `.ppt` and macro-enabled `.pptm` are rejected with a request to re-save as `.pptx`, to avoid a LibreOffice dependency on the server and to keep macros off it. A slide built on a House template layout (`IAM_TITLE`, `IAM_SECTION`, `IAM_SUMMARY`, `IAM_KPI`, `IAM_CHART`, `IAM_TABLE`, `IAM_TWO_COLUMN`, `IAM_CLOSING`) maps to its Section exactly by layout name and named placeholders; any other slide is matched heuristically and confirmed by the user.

## Consequences

- Renaming a layout or placeholder in the House template breaks import of older decks; the Admin upload must validate that every required layout and placeholder name is present.
- Continuation slides (same layout, repeated title or "(cont.)", same table header) are merged back into one Section.
- Charts import as images, SmartArt as text; animations and media are dropped.
- The original file is kept in the database with the import record.
