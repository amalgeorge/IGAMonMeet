# 33 JSON export for Word

Blocked by: 32

Spec §5. Asked for after the grilling: "add an option to export as Json which can be eventually converted to Word doc".

- `POST /api/meetings/{id}/export/json`, same options as PPTX; schema `igamonmeet.document/1` (docs/json-export.md).
- Document-shaped blocks per Section; Theme name, colours and fonts in `meeting.theme`.
- "JSON (for Word)" button in the Export panel.
- `tools/json-to-word/json_to_docx.py` converter and a sample file.
- Audit `EXPORTED`.
