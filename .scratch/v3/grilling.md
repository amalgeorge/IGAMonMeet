# v3 grilling (2026-11-09)

Request: custom arrangement of KPI and other slides by Admin; clickable links between slides like interactive PPTX; Section groups "do nothing"; more table columns, commas in column names, CSV upload; Incidents becomes SITREP (Type, New, Closed, Pending, Above SLA); rich text in every Section; suggest new Sections. Later: Tomcat will be hosted on Linux.

| Q | Decision |
|---|---|
| Q1 | Drag-and-drop ordering of Sections and of KPIs inside a KPI Section, plus free arrangement on slides (Q9). |
| Q2 | Agenda items link to their Section; "↩ Agenda" on every content slide; group divider slides link to their Sections. In Slides view and exported PPTX. |
| Q3 | Section groups: heading and divider did not appear. Cause seen: group set to Available. Explain Visibility on the page; preview shows a switched-off divider marked "not in this meeting"; verify Shown groups render. |
| Q4 | One input per column (commas allowed). Admins define columns; editors add columns only in Text table blocks. Limit 12. |
| Q5 | Import CSV on every table. Header row maps by name; in Text table blocks a mismatch makes the file's headers the columns. Editor chooses replace or append after a preview. |
| Q6 | Incidents becomes SITREP: Type, New, Closed, Pending, Above SLA. Whole numbers, automatic Total row, Above SLA > 0 red. Type from an Admin list, free text allowed. Held Meetings keep Incidents; new and Preparing get SITREP. |
| Q7 | Rich text (bold, italic, bullets, numbered, sub-bullets, links) in every Section, replacing commentary boxes. Same slide if it fits, else continuation slide. PPTX and JSON keep formatting. |
| Q8/Q11 | New Sections, Available, in a "Programme" group: Access reviews (Campaign, Scope, % complete, Due, Overdue reviewers, Status); Privileged ID (KPIs: vault coverage %, sessions recorded %, break-glass uses; targets blank); Joiner-Mover-Leaver (Type, Requests, Completed + Total); Application onboarding pipeline (Application, Stage, ETA, Blocker, Status); Decisions needed (Decision, Context, Needed by, Owner); Wins and recognition (bullets). |
| Q9 | Layout editor per Section, Admin, in the Template: blocks and individual KPI tiles on a 12×8 grid, drag and resize, live preview with sample data. Overflow continues on a slide with the same layout. PPTX places shapes at the same positions. |
| Q10 | Automatic agenda slide after the title slide, clickable; Admin can switch it off (links go with it); second agenda slide past ~12 entries. (No answer; recommendation taken.) |
| Linux | Tomcat will run on Linux. Nothing Windows-only in the app; Linux install script and fonts for slide text measurement. |
