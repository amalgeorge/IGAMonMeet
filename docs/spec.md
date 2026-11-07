# IAM Monthly Meeting: v1 specification

Vocabulary follows [CONTEXT.md](../CONTEXT.md). Decisions with trade-offs are in [docs/adr](adr/). UI mockups: the "IAM Monthly Meeting Hub" design canvas.

## Goal

IAM Engineering and IAM Operations prepare one Meeting per month in a shared, structured form, present it as PowerPoint-quality slides in the browser, export it to `.pptx`, import last month's or ad-hoc decks, and keep a permanent, auditable history.

## Platform

| Concern | Choice |
|---|---|
| Runtime | OpenJDK 17, Tomcat 9, one node, TLS on the Tomcat connector |
| Backend | Spring Boot 2.7 (WAR), Spring MVC REST under `/api`, Spring Security 5.8, Hibernate 5.6 / JPA |
| Database | Dedicated MSSQL database on the IIQ instance, Flyway migrations, times stored in UTC |
| Frontend | React 18 + Vite, Motion, Apache ECharts, bundled into the WAR |
| PPTX | Apache POI XSLF |
| Display time zone | Asia/Singapore |
| Browsers | Latest Edge and Chrome |

## 1. Accounts and access

1. **Registration.** Full name, username, work email, password, confirm password. Creates a Pending user. Usernames are unique forever (rejected and disabled Users keep theirs).
2. **Sign in.** Username and password. Pending users see "Awaiting admin approval". Wrong username and wrong password show the same message.
3. **Password rules.** At least 12 characters; not one of the User's last 5 passwords; stored as BCrypt.
4. **Lockout.** 5 failed sign-ins lock the account for 15 minutes; an Admin can unlock early.
5. **Session.** 30-minute idle timeout; new session ID at sign-in; CSRF token on every state-changing request; secure, HTTP-only cookies.
6. **Roles.** Admin and End user only. Access rights come from the role; the sign-in screen shows no role choice.
7. **Forgot password.** Creates a request in the Admin queue. The Admin presses Reset; the app shows a one-time temporary password once; the User must change it at next sign-in.
8. **Admin recovery.** `dbo.usp_AdminRecovery @username` (see ADR 0002). The `/recover?token=` page lets the Admin set a new password.
9. **First Admin.** Created by a one-time setup step; must change password at first sign-in.
10. **User management (Admin).** Approve or reject, disable or enable, unlock, reset password, promote to Admin or demote to End user. The last active Admin cannot be disabled or demoted.
11. **Feature toggles (Admin).** Self-registration, approval step, forgot-password requests, Admin queue badge. Changes ask for confirmation and are audit-logged.

## 2. Template

1. Admin edits the Template: add, rename, reorder and remove Sections; set each Section's Team tag, Required flag, House template layout and Overflow rule.
2. KPIs: name, unit, target, direction, amber threshold, Required flag, Section.
3. Each change creates a new Template version. Meetings keep the version they were created from.
4. **House template.** Ships with `IAM_Monthly_Meeting_Template.pptx`. Admin may upload a replacement; the upload is rejected unless every required layout and placeholder name exists. On upload, theme colours, fonts, background images and logo placement are extracted for in-app slides.

## 3. Meetings

1. **Create (Admin).** Pick month and Meeting date. Sections come from the current Template; open Action items from the previous Meeting are carried forward.
2. **Edit window.** Any User may edit until 23:59 SGT two days after the Meeting date; after that Admin only. Labels Preparing / Held follow the Meeting date.
3. **Concurrent edits.** Opening a Section for editing takes a Section lock (released on save, leave, or 15 minutes idle). Every save carries the version it started from; if someone saved first, the User sees both versions and picks.
4. **Autosave.** Edits save automatically and update the current state.
5. **KPI status.** Computed from value and target. Override needs a reason, shown on the slide.
6. **Action items.** Free-text owner, Team tag, due date, open/closed. Overdue items are highlighted and sort first.
7. **Comments.** Threaded per Section; can be resolved; visible to all Users.
8. **Delete (Admin).** Soft delete; hidden immediately; restorable for 30 days; then purged.

## 4. Views of one Meeting

The same Meeting is shown in three views, switched from the same control in every view.

1. **Document view.** Section outline with progress (Required sections only), Section editors, live slide preview rail, Section lock indicators, warnings such as "Action items will use 3 slides".
2. **Slides view.** PowerPoint-style: thumbnails, current slide, speaker notes, layout and transition/build settings per slide, Present, From current slide, Presenter view.
3. **Dashboard view.** KPI tiles with 6-month trends, roadmap health, open and overdue Action items, Comments.

## 5. Slide plan and presenting

1. One server-side engine builds the Slide plan (ADR 0004). Default Overflow rules:
   - Tables: continue on new slides, header row repeated, rows never split, titles "(1 of 3)".
   - KPI tiles: 4 per slide; more go to further slides.
   - Text: shrink one step (18pt to 16pt, never below 14pt), then continue.
   - Charts: never split; long series show the most recent months.
2. **Show.** Browser full screen, 16:9 letterboxed on black, idle cursor hidden, slide transitions (fade, push, none) and element builds. Keys: → / Space / Enter / click next; ← back; number + Enter jump; B / W black or white screen; Home / End; Esc end.
3. **Presenter view.** Second window: current slide, next slide, notes, elapsed timer, controls; kept in sync with the audience window.
4. Motion is reduced to cross-fades when the OS "reduce motion" setting is on.

## 6. PPTX import and export

1. **Import (any User, within the Edit window).** `.pptx` / `.potx`, maximum 50 MB. Wizard: upload, map slides to Sections (House template layouts map exactly; others matched with a confidence label; Continuation slides merged; unmatched go to Attachments), review, apply. When a Section already has content: keep mine and add the slide as a note, or replace. Charts import as images, SmartArt as text. The original file is stored with the import record. A Version is taken.
2. **Export (any User).** Choose Sections and extras (speaker notes, comments appendix, version footer). Output `.pptx` built on the House template from the Slide plan, or PDF.

## 7. History and audit

1. **Versions.** Taken on import, restore, at 23:59 SGT on the Meeting date, and by "Save version". Any User can view Versions, diff two Versions, and an Admin or (within the Edit window) any User can restore one.
2. **Audit log (Admin).** Every field change (who, when, old, new), sign-in, failed sign-in, registration, approval, role change, reset, toggle change, import, export, delete. Downloadable as CSV. Kept forever.

## Out of scope for v1

SSO; email notifications; Teams or Slack messages; a Final or approval workflow; `.ppt`, `.pptm`, Keynote, Google Slides or PDF import; multiple Tomcat nodes; browsers other than Edge and Chrome.
