# JSON export (`igamonmeet.document/1`)

`POST /api/meetings/{id}/export/json` returns a Meeting as a JSON document built to be turned into Word. The body is the same as the PPTX export:

```json
{ "sections": null, "notes": true, "comments": false, "versionStamp": false }
```

`sections: null` means every Section shown in the Meeting; a list of Section keys limits the export. `notes` adds speaker notes, `comments` adds comment threads. `versionStamp` is ignored. Any signed-in user may export; each export is audited as `EXPORTED`.

The file is document-shaped, not slide-shaped: no slide splitting, no overflow rules, and Sections kept off the slides are included. A converter walks `sections` in order and renders each block. It can ignore block types it does not know, so later additions do not break it. A breaking change gets a new schema id.

## 1. Top level

| Field | Type | Meaning |
|---|---|---|
| `schema` | string | Always `igamonmeet.document/1` |
| `exportedAt` | ISO instant | When the file was made (UTC) |
| `exportedBy` | string | Username |
| `meeting` | object | §2 |
| `sections` | array | §3, in Meeting order |
| `actionItems` | array | Every action item of the Meeting, §4.8 |

## 2. `meeting`

`id`, `title`, `month` (`2026-11`), `monthText` (`November 2026`), `date` (`2026-11-19`), `dateText` (`Thursday 19 November 2026`), `label` (`Preparing`, `Held` …), `templateVersion`, `requiredSections`, `requiredFilled`, and `theme`:

```json
{ "name": "IAM Red", "version": 1,
  "colors": { "primary": "C8102E", "accent": "ED1B2E", "text": "1A1A1A", "background": "FFFFFF",
              "onTarget": "1E7D43", "belowTarget": "A35D00", "offTarget": "C8102E" },
  "fonts": { "heading": "Arial", "body": "Arial" } }
```

Colours are 6-digit hex without `#`.

## 3. Section

| Field | Type | Meaning |
|---|---|---|
| `key`, `name` | string | Section key and title |
| `team` | string | `Engineering`, `Operations` or `Shared` |
| `kind` | string | Source kind (`SUMMARY`, `KPI`, `CHART`, `TABLE`, `TWO_COLUMN`, `TEXT`, `ACTION_ITEMS`); for information only, render the blocks |
| `group` | object or null | `{ key, name }` of the Section group; start a group heading when it changes |
| `required`, `filled`, `onSlides` | boolean | Progress and whether the Section is on the slides |
| `updatedBy`, `updatedAt` | string or null | Last edit |
| `blocks` | array | §4; may be empty when nothing is filled yet |
| `notes` | string | Only with `notes: true` and when present |
| `comments` | array | Only with `comments: true`: `{ author, body, resolved, createdAt, replies: [{ author, body, createdAt }] }` |

## 4. Blocks

Each block has a `type`.

1. **`paragraph`** — `{ "text": "…" }`. Plain text; line breaks are `\n`.
2. **`bullets`** — `{ "items": ["…"] }`.
3. **`stats`** — `{ "items": [{ "value": "12", "label": "Apps onboarded" }] }`. Headline numbers of a summary.
4. **`table`** — `{ "columns": ["…"], "rows": [["…"]], "style": { … } }`. Every row has one cell per column; cells are text. `style`:
   - `widths`: column widths in percent, or null for even widths
   - `align`: `left`, `center` or `right` per column, or null
   - `statusColumn`: index of the column whose words (`On track`, `At risk`, `Off track` …) colour the row, or null
   - `total`: the last row is the total row the app computed (sums of number columns); show it bold
   - `wholeNumbers`: columns holding whole numbers only
   - `alertColumn`: column whose cells above 0 are shown red (SITREP "Above SLA"), or null
   - `listColumn`: column whose values come from the Admin's SITREP types, or null
   - `banded`: alternate rows shaded
5. **`kpis`** — `{ "items": [KPI] }`. One per KPI shown in the Section, in order. KPI fields: `key`, `name`, `subtitle`, `description`, `valueType`, `value` (number, text or null), `unit`, `display` (the value as shown on the slide, e.g. `96.4%`), `target`, `targetLine`, `deltaLine`, `status` (`ON_TARGET`, `BELOW_TARGET`, `OFF_TARGET` or null), `statusLabel`, `overridden`, `overrideReason`, `required`. Prefer `display`, `targetLine`, `deltaLine` and `statusLabel` for text; they follow the Admin's labels and formats.
6. **`chart`** — `{ "kpiKey", "title", "unit", "target", "labels": ["Sep", …], "values": [94.1, …] }`. The KPI's trend, oldest first, up to 12 Meetings. Draw a chart or a one-row table.
7. **`columns`** — `{ "left": { "heading", "items": [] }, "right": { "heading", "items": [] } }`. Two lists side by side.
8. **`actionItems`** — `{ "items": [{ "title", "owner", "team", "due", "status", "overdue", "carriedForward" }] }`. `due` is an ISO date or null; `status` is `OPEN` or `CLOSED`.

9. **`richText`** — `{ "blocks": [...] }`. Formatted text (spec-v3 §2), last block of a Section:
   - `{ "type": "p", "runs": [Run] }` a paragraph
   - `{ "type": "ul" | "ol", "items": [{ "runs": [Run], "items": [{ "runs": [Run] }] }] }` a bulleted or numbered list, one sub-level
   - Run: `{ "text", "b"?: true, "i"?: true, "href"?: "https://…" }`

## 5. Converting to Word

`tools/json-to-word/json_to_docx.py` is a ready converter:

```
pip install python-docx
python tools/json-to-word/json_to_docx.py November_2026.json
```

It writes `November_2026.docx`: title and date, a heading per group and Section, KPI tables with status colours, styled tables, bullets, notes and comments, using the Theme's fonts and primary colour. `tools/json-to-word/sample.json` is an example file.
