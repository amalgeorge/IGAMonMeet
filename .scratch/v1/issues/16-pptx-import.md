# 16 PPTX import wizard

Blocked by: 09, 13

Spec §6.1, ADR 0005.

- Accept .pptx/.potx up to 50 MB; reject .ppt/.pptm with a 're-save as .pptx' message.
- Exact mapping by layout name; heuristic mapping with confidence otherwise; continuation slides merged.
- Merge choice per Section; original file stored; Version taken; audit-logged.
