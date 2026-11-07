# v1 tickets

Local tracker: one file per ticket in `issues/`. Work a ticket only when everything in its "Blocked by" line is done.

| # | Ticket | Blocked by | Status |
|---|---|---|---|
| 01 | Build, deploy and CI pipeline | none | Done |
| 02 | Local accounts: register, approve, sign in | 01 | Done |
| 03 | Audit log | 02 | Done |
| 04 | Admin: user management, Admin queue, password reset | 03 | Done |
| 05 | First Admin setup and DBA recovery procedure | 04 | |
| 06 | Feature toggles | 04 | |
| 07 | Template: Sections and KPIs | 02 | |
| 08 | Meetings: create, list, soft delete | 07 | |
| 09 | Document view: edit Sections | 08 | |
| 10 | KPI values and computed status | 09 | |
| 11 | Action items and Comments | 09 | |
| 12 | Slide plan engine | 09, 13 | |
| 13 | House template: bundled default and Admin upload | 07 | |
| 14 | Slides view, show and Presenter view | 12 | |
| 15 | PPTX export | 12 | |
| 16 | PPTX import wizard | 09, 13 | |
| 17 | Versions, diff, restore, meeting-date snapshot | 09 | |
| 18 | Dashboard view | 10, 11 | |
