# IAM Monthly Meeting

The place where IAM Engineering and IAM Operations prepare, review and present their monthly meeting, and keep a record of every past meeting.

## Meetings

**Meeting**:
One month's IAM review, held on a single date, holding the content of every Section for that month.
_Avoid_: Session, deck, report

**Meeting date**:
The calendar day the Meeting is held, in Singapore time. A Meeting has a date, not start and end times.

**Edit window**:
The period during which any User may change a Meeting: from its creation until 23:59 SGT two days after the Meeting date. After it closes, only an Admin may change the Meeting.
_Avoid_: Lock, freeze, finalisation

**Preparing / Held**:
Information-only labels: a Meeting is Preparing before its Meeting date and Held from then on. They never restrict editing.
_Avoid_: Draft, In review, Final

**Section**:
One titled part of a Meeting (for example Key metrics, Roadmap, Action items), belonging to a Team tag, whose content is the single source for the Document view, slides, dashboard and export.
_Avoid_: Page, slide, block

**Section group**:
An optional, Admin-defined heading in the Template that gathers several Sections under one title (for example "Operations review"). A Section belongs to at most one Section group.
_Avoid_: Category, chapter

**Section lock**:
A short-lived claim one User holds while editing a Section, so another User sees it is being edited.

## Template

**Template**:
The Admin-maintained list of Sections and KPIs that every new Meeting is created from. A Meeting keeps the Template version it was created with.
_Avoid_: Layout, form

**Required section**:
A Section the Template marks as expected to be filled; optional Sections may stay empty without counting as a gap.

**House template**:
The PowerPoint master file whose named layouts define how Sections become slides on export and are recognised on import.
_Avoid_: Theme, master deck

**Theme**:
A named set of colours, fonts and slide backgrounds, maintained by an Admin, that styles a Meeting's slides on top of the House template. Each Meeting uses one Theme.
_Avoid_: Skin, style, template (Template means the Sections and KPIs)

**Overflow rule**:
The per-Section setting for content that does not fit one slide: continue on new slides, shrink to fit, or show the first rows and move the rest to an appendix slide.

## Content

**KPI**:
A measured value defined in the Template with a target, unit, direction (higher or lower is better) and amber threshold.
_Avoid_: Metric (in UI copy, "Key metrics" names the Section, not a single KPI)

**KPI status**:
On target, Below target or Off target, computed from a KPI's value and target. An editor may override it, with a reason shown on the slide.
_Avoid_: RAG

**Action item**:
A task agreed in a Meeting, with a free-text owner, a Team tag, a due date and an open or closed state. Open Action items carry forward into the next Meeting.

**Team tag**:
Engineering, Operations or Shared, labelling which team a Section or Action item belongs to. It is not a property of a User and grants no permission.
_Avoid_: Team role, department

**Comment**:
A threaded remark attached to a Section, which can be marked resolved.

## People and access

**User**:
A person with a local account in this application.
_Avoid_: Account (except for the login credential itself), member

**Admin**:
A User who approves registrations, manages Users, the Template and the House template, creates and deletes Meetings, and may edit a Meeting after its Edit window.

**End user**:
A User who is not an Admin. Edits Meetings within the Edit window, comments, imports and exports.
_Avoid_: Viewer, Editor, Chair

**Pending user**:
A User who has registered but whom no Admin has approved yet. Cannot sign in.

**Admin queue**:
The Admin's list of work waiting on them: pending registrations and password-reset requests.

**Feature toggle**:
An Admin setting that switches an optional capability on or off, such as self-registration or forgot-password requests.

## Slides and history

**Slide plan**:
The ordered slides generated from a Meeting's Sections by applying the House template and each Section's Overflow rule. Slides are output, never edited one by one.
_Avoid_: Deck (for the generated plan)

**Continuation slide**:
An extra slide a Section's content spills onto under its Overflow rule.

**PPTX import**:
Reading a `.pptx` or `.potx` file into a Meeting's Sections, keeping the original file with the import record.

**Version**:
A named snapshot of a Meeting, taken on import, restore, at 23:59 SGT on the Meeting date (the "as presented" snapshot), or by hand.

**Audit log**:
The permanent, field-level record of every change, sign-in and admin action. Visible to Admins only.
_Avoid_: History (History means Versions and their diffs, visible to all Users)
