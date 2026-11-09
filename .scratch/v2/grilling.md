# v2 grilling: decisions so far

Terms are in [CONTEXT.md](../../CONTEXT.md).

| # | Topic | Decision |
|---|---|---|
| Q1 | "Category" | Means Sections, plus a new optional **Section group** level. Team tags stay fixed. |
| Q2 | KPI additions | More display fields and new value types. Calculated and breakdown KPIs deferred. |
| Q3 | Tables on slides | Table block inside a Section's rich content, plus richer table styling. No free-form slide editing (ADR 0004 stands). |
| Q4 | Theme scope | Several named Themes kept by Admin; each Meeting picks one; one Theme is the default for new Meetings. |
| Q5 | Fonts | Safe list (Arial, Calibri, Segoe UI, Cambria, Georgia, Verdana) plus brand fonts installed on the server. |
| Q6 | KPI list | KPI library (one Admin list; Sections pick from it; one trend history per KPI). Open: who adds, see Q15. |
| Q7 | Section groups | One level; optional; divider slide (Admin can switch off per group); collapsible in outline with progress; Dashboard groups tiles; import skips dividers. |
| Q8 | KPI value types | Number, Percentage, Count, Duration, Currency, Text status. All optional: Admin picks per KPI, Number by default. |
| Q9 | KPI display fields | Subtitle, description (speaker notes + Dashboard tooltip), decimal places. Status labels renamed globally by Admin. |
| Q10 | Table block | Text Sections become rich content (paragraph, bullet list, table blocks). Editor sets columns; max 8 columns; rows continue with repeated header. |
| Q11 | Table styling | Column width share, alignment, status-coloured rows, total row, banded rows. Admin sets for TABLE Sections; editor for table blocks. |
| Q12 | Theme contents | Overrides House template: colours (incl. status colours), heading and body font, title/divider/content backgrounds (colour or 16:9 PNG/JPG up to 5 MB), logo on/off or replacement, preview. |
| Q13 | Theme changes | Live until Held: Preparing Meetings follow the latest Theme version; frozen at their version from the Meeting date. |
| Q14 | Import styling | Content only; the file's styling is ignored. |
