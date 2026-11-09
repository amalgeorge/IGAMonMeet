# v2 grilling: decisions so far

Terms are in [CONTEXT.md](../../CONTEXT.md).

| # | Topic | Decision |
|---|---|---|
| Q1 | "Category" | Means Sections, plus a new optional **Section group** level. Team tags stay fixed. |
| Q2 | KPI additions | More display fields and new value types. Calculated and breakdown KPIs deferred. |
| Q3 | Tables on slides | Table block inside a Section's rich content, plus richer table styling. No free-form slide editing (ADR 0004 stands). |
| Q4 | Theme scope | Several named Themes kept by Admin; each Meeting picks one; one Theme is the default for new Meetings. |
| Q5 | Fonts | Safe list (Arial, Calibri, Segoe UI, Cambria, Georgia, Verdana) plus brand fonts installed on the server. |
| Q6 | KPI list | KPI library (one Admin list; Sections pick from it; one trend history per KPI). See Q15. |
| Q7 | Section groups | One level; optional; divider slide (Admin can switch off per group); collapsible in outline with progress; Dashboard groups tiles; import skips dividers. |
| Q8 | KPI value types | Number, Percentage, Count, Duration, Currency, Text status. All optional: Admin picks per KPI, Number by default. |
| Q9 | KPI display fields | Subtitle, description (speaker notes + Dashboard tooltip), decimal places. Status labels renamed globally by Admin. |
| Q10 | Table block | Text Sections become rich content (paragraph, bullet list, table blocks). Editor sets columns; max 8 columns; rows continue with repeated header. |
| Q11 | Table styling | Column width share, alignment, status-coloured rows, total row, banded rows. Admin sets for TABLE Sections; editor for table blocks. |
| Q12 | Theme contents | Overrides House template: colours (incl. status colours), heading and body font, title/divider/content backgrounds (colour or 16:9 PNG/JPG up to 5 MB), logo on/off or replacement, preview. |
| Q13 | Theme changes | Live until Held: Preparing Meetings follow the latest Theme version; frozen at their version from the Meeting date. |
| Q14 | Import styling | Content only; the file's styling is ignored. |
| Q15 | Who adds | Admin adds Sections and KPIs in the Template only. Editors switch on, for one Meeting, items Admin marked Available. |
| Q16 | Visibility | Each Section, Section group and KPI is Shown, Available or Retired (nothing deleted; history kept). Admin can also keep a Section off the slides while it stays in Document and Dashboard. |
| Q17 | KPI tiles per slide | Admin picks per KPI Section: 2, 3, 4, 6 or 8 tiles, or a table layout. Default 4. |
| Q18 | Order | Template order only; editors cannot reorder within a Meeting. |
| Q19 | Switching items | Within the Edit window (Admin after). On: appears empty at its Template position. Off: only while empty; with content Admin only, and the content is hidden, not deleted. Audit-logged. |
| Q20 | Catalogue | Preparing Meetings pick from the latest Template version's Available items; Held Meetings stay as they are. |
| Q21 | Data migration | KPIs move into the KPI library with keys unchanged (trend history continues); Text Sections become rich content with one paragraph block; Flyway, no visible change. |
| Q22 | Theme choice | Admin any time; any editor while the Meeting is Preparing. |
| Q23 | PPTX and Themes | Export writes Theme colours and fonts into the PowerPoint theme and backgrounds into title/divider/content layouts. Import maps PowerPoint tables into table blocks. House template check adds the divider layout. |
| Q24 | Build order | 1 Visibility and Section groups; 2 KPI library, value types, display fields, tiles per slide; 3 rich content, table blocks, table styling; 4 Themes with export and import. CI green before the next step. |

Grilling complete. Next: /to-spec (docs/spec-v2.md), then /to-tickets (.scratch/v2/issues/).
