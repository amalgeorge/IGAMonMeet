# IAM Monthly Meeting: v2 specification

Builds on [v1](spec.md). Vocabulary follows [CONTEXT.md](../CONTEXT.md). Decisions and their numbers (Q1 to Q24) are in [.scratch/v2/grilling.md](../.scratch/v2/grilling.md); ADR 0008 covers Preparing versus Held. Platform is unchanged (ADR 0001). Slides are still generated from Sections, never edited one by one (ADR 0004).

## 1. Visibility and Section groups

1. **Visibility (Admin, Template).** Every Section, Section group and KPI is Shown, Available or Retired (Q16).
   - Shown: in every new Meeting.
   - Available: hidden until an editor switches it on for one Meeting.
   - Retired: hidden from new Meetings; past Meetings and trend history are kept. Nothing is deleted.
2. **Off slides.** Admin can keep a Section in the Document and Dashboard views but off the Slide plan and export.
3. **Switching items in a Meeting** (Q19, Q20).
   - Any User within the Edit window; Admin after it closes.
   - The catalogue is the latest Template version's Available items while the Meeting is Preparing; Held Meetings stay as they are (ADR 0008).
   - On: the item appears empty at its Template position.
   - Off: only while empty. With content, Admin only, and the content is hidden, not deleted.
   - Every switch is audit-logged.
4. **Order.** Template order only; editors cannot reorder (Q18).
5. **Section groups** (Q7).
   - One level; a Section belongs to at most one group; ungrouped Sections stay at the top level.
   - Each group has a divider slide, which Admin can switch off per group.
   - Document outline: collapsible group headings with progress per group.
   - Dashboard: KPI tiles grouped under group titles.
   - PPTX import recognises divider slides and skips them.
   - Divider slides use the existing `IAM_SECTION` layout (title: the group name; body: its Sections), so the House template contract does not change. A grouped Section gets its group's divider instead of the team divider.

## 2. KPIs

1. **KPI library** (Q6, Q15). One Admin-maintained list of KPIs. Sections pick KPIs from it; a KPI used in several Sections keeps one trend history. Existing KPIs move into the library with their keys unchanged (Q21).
2. **Value types** (Q8). Admin picks one per KPI; Number by default.
   - Number, Percentage, Count (whole numbers), Duration (entered as `2h 30m`), Currency (code set by Admin, SGD by default).
   - Text status: an Admin-defined option list, each option mapped to a KPI status. No trend line; a coloured history strip instead.
3. **Display fields** (Q9). Per KPI: subtitle, description (speaker notes and Dashboard tooltip), decimal places. All optional.
4. **Status labels.** Admin renames On target, Below target and Off target once, for all KPIs.
5. **Tiles per slide** (Q17). Per KPI Section: 2, 3, 4, 6 or 8 tiles, or a table layout. Default 4.

## 3. Rich content and tables

1. **Rich content** (Q10). Text Sections hold an ordered list of blocks: paragraph, bullet list, table. Existing text becomes one paragraph block (Q21).
2. **Table blocks.** The editor sets the columns; up to 8 columns; any number of rows. Rows continue onto further slides with the header repeated, never split.
3. **Table styling** (Q11). For TABLE Sections (Admin, in the Template) and table blocks (editor):
   - column width as a share of the table width
   - left, centre or right alignment
   - a status column that colours the row (On track green, At risk amber, Off track red)
   - a total row summing number columns
   - banded rows on or off
4. **Import** (Q23). A PowerPoint table on an imported slide becomes a table block in the matched Section.

## 4. Themes

1. **Theme editor (Admin)** (Q12). Several named Themes; one is the default for new Meetings. A Theme overrides the House template, never replaces it:
   - colours: primary, accent, text, slide background, and the three KPI status colours
   - fonts: heading and body, from the safe list (Arial, Calibri, Segoe UI, Cambria, Georgia, Verdana) or a brand font installed on the server (Q5)
   - backgrounds: title, divider and content slides, each a colour or an uploaded PNG or JPG (up to 5 MB, cropped to 16:9)
   - logo: on or off, or a replacement image
   - preview of sample slides before saving
2. **Choosing a Theme** (Q22). Admin at any time; any editor while the Meeting is Preparing.
3. **Versions** (Q13, ADR 0008). Each save is a new Theme version. A Preparing Meeting follows the latest version; from the Meeting date it is frozen at the version in use.
4. **Export** (Q23). Writes Theme colours and fonts into the PowerPoint theme and backgrounds into the title, divider and content layouts, so the file stays editable in PowerPoint. PDF via browser print uses the same styling.
5. **Import** (Q14). Content only; the file's styling is ignored.

## Out of scope for v2

Calculated KPIs and KPI breakdowns; ad-hoc KPIs per Meeting; editor reordering of Sections; free-form slide editing; uploaded font files; creating a Theme from an imported deck; per-Section backgrounds.
