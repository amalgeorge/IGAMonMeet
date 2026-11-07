# 12 Slide plan engine

Blocked by: 09, 13

Spec §5.1, ADR 0004.

- Server builds the Slide plan from Sections, layouts and Overflow rules.
- Text measured with the template font (Arial or Liberation Sans on the server).
- Default rules: tables continue with repeated header, KPIs 4 per slide, text shrinks one step then continues, charts never split.
- Document view shows 'will use N slides' warnings.
- Unit tests with long tables, 9 KPIs, long text, long chart series.
