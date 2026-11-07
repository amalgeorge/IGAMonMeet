# 13 House template: bundled default and Admin upload

Blocked by: 07

Spec §2.4, ADR 0005.

- Bundled default: src/main/resources/templates/IAM_Monthly_Meeting_Template.pptx (regenerate with tools/template).
- Admin upload validates required layout and placeholder names; stores versions.
- Extract theme colours, fonts, background images and logo placement for in-app slides.
