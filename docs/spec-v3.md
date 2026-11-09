# IAM Monthly Meeting: v3 specification

Builds on [v2](spec-v2.md). Decisions Q1 to Q11 are in [.scratch/v3/grilling.md](../.scratch/v3/grilling.md). Slides are still generated from Sections (ADR 0004); the Layout editor arranges where generated content goes, it does not make slides hand-edited.

## 1. Tables

1. **Columns** (Q4). Edited as one input per column with Add, Remove and reorder, in the Template and in Text table blocks. Column names may contain commas. Up to 12 columns.
2. **CSV import** (Q5). Every table (Template tables and Text table blocks) has "Import CSV".
   - The first row is the header. Columns are matched by name, ignoring case and spaces.
   - Template tables: unmatched file columns are ignored and listed; missing columns stay empty.
   - Text table blocks: if no header matches, the file's headers become the columns.
   - The editor chooses Replace rows or Append rows. A preview shows the rows before anything changes.
   - UTF-8 with or without BOM; comma or semicolon separators; quoted fields with commas and line breaks.
3. **SITREP** (Q6). Replaces the Incidents Section in the default Template.
   - Columns Type, New, Closed, Pending, Above SLA. Count columns take whole numbers ≥ 0.
   - Total row added automatically on slides, exports and the Dashboard.
   - Above SLA > 0 shown red.
   - Type: pick from the Admin's SITREP types (Settings) or type free text.
   - Held Meetings keep their Incidents table (ADR 0008); Preparing Meetings move to SITREP with an empty table, old rows kept on the Meeting for reference.

## 2. Formatted text (Q7)

1. Every Section has a rich text box, replacing the commentary boxes (KPI commentary, chart commentary, summary points stay as data, commentary moves to rich text).
2. Formatting: bold, italic, bulleted list, numbered list, one level of sub-bullets, links. Paste strips other formatting.
3. Stored as a small JSON document (paragraphs, lists, marks), never raw HTML.
4. Slides: text goes on the Section's slide when it fits, otherwise onto continuation slides. PPTX keeps bold, italic, bullets, numbering and hyperlinks. JSON export carries the same structure.

## 3. New Sections (Q11)

Added to the default Template as Available in a new group "Programme":
1. Access reviews / certifications: table Campaign | Scope | % complete | Due | Overdue reviewers | Status (status-coloured).
2. Privileged ID: KPI Section with library KPIs Vault coverage (%, higher), Sessions recorded (%, higher), Break-glass uses (number, lower). Targets blank.
3. Joiner-Mover-Leaver: table Type | Requests | Completed, Total row.
4. Application onboarding pipeline: table Application | Stage | ETA | Blocker | Status (status-coloured).
5. Decisions needed: Text Section with a table Decision | Context | Needed by | Owner.
6. Wins and recognition: Text Section with bullets.

## 4. Ordering and Section groups

1. **Drag and drop** (Q1). Sections in the Template, and KPIs inside a KPI Section, reorder by drag and drop; the ↑/↓ buttons stay for keyboard use.
2. **Section groups** (Q3). The Template page explains Visibility next to each group. The Slides preview shows the divider of a switched-off group greyed with "not in this meeting". Shown groups always render heading and divider.

## 5. Agenda and links (Q2, Q10)

1. An agenda slide follows the title slide, listing groups and Sections in order. Past about 12 entries it continues on a second agenda slide.
2. Each agenda entry links to the Section's first slide. Every content slide has "↩ Agenda". Group divider slides list and link their Sections.
3. Links work in the Slides view (click to jump) and in the exported PPTX (internal slide hyperlinks).
4. Admin can switch the agenda slide off in the Template; the links go with it.

## 6. Layout editor (Q9)

1. Per Section, the Admin opens "Layout" in the Template: a 16:9 canvas with a 12 × 8 grid and a live preview using sample data.
2. Movable items: title, content block (table, chart, text), each KPI tile, rich text box. Drag to move, drag corners to resize, snapped to the grid. "Reset to default" restores the generated layout.
3. Stored in the Template version with the Section. Every Meeting on that version uses it.
4. Overflow continues on further slides with the same layout.
5. PPTX places shapes at exactly the same positions; the PDF print view follows the in-app renderer.

## 7. Linux hosting

1. The WAR runs unchanged on Tomcat 9 on Linux. Nothing in the app depends on Windows paths, fonts or services.
2. Slide text measurement uses the fonts installed on the server. The Linux install script installs metric-compatible fonts (Liberation for Arial/Times/Courier, Carlito for Calibri) and runs the JVM headless.
3. `tools/linux/install-server.sh` for RHEL 8/9 (and Rocky/Alma): OpenJDK 17 from RHEL AppStream, Tomcat 9 as a systemd service, TLS on 8443 (self-signed unless a certificate is given), firewalld port, SELinux contexts, setenv.sh with the database settings (readable by the tomcat user only), WAR from a file or from CI, health check.
4. The database is an existing remote SQL Server: the script asks for host, port, database, user and password and does not install SQL Server. Windows keeps `Install-TestServer.ps1` for testing.
5. CI already builds and tests on Linux; a Rocky Linux 9 container smoke test (`rhel-smoke`) deploys the WAR on Tomcat 9 and checks the health endpoint.
