#!/usr/bin/env python3
"""Convert an IGAMonMeet JSON export (schema igamonmeet.document/1) into a Word document.

Usage:  python json_to_docx.py meeting.json [out.docx]
Needs:  pip install python-docx

The JSON layout is described in docs/json-export.md. Unknown block types are skipped,
so newer exports still convert with an older copy of this script.
"""
import json
import sys
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor

SCHEMA = "igamonmeet.document/1"
ALIGN = {"left": WD_ALIGN_PARAGRAPH.LEFT, "center": WD_ALIGN_PARAGRAPH.CENTER, "right": WD_ALIGN_PARAGRAPH.RIGHT}
ROW_FILL = {"ON_TARGET": "E3F3E9", "BELOW_TARGET": "FFF3DC", "OFF_TARGET": "FBE3E6"}


def rgb(hex_, default):
    h = (hex_ or default).lstrip("#")
    return RGBColor(int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def shade(cell, hex_):
    pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_)
    pr.append(shd)


def style_document(doc, theme):
    fonts = theme.get("fonts") or {}
    colors = theme.get("colors") or {}
    body = doc.styles["Normal"]
    body.font.name = fonts.get("body", "Arial")
    body.font.size = Pt(10.5)
    for name in ("Title", "Heading 1", "Heading 2", "Heading 3"):
        st = doc.styles[name]
        st.font.name = fonts.get("heading", "Arial")
        rfonts = st.element.rPr.rFonts
        for attr in ("w:asciiTheme", "w:hAnsiTheme", "w:cstheme", "w:eastAsiaTheme"):
            rfonts.attrib.pop(qn(attr), None)  # theme fonts would win over the named font
        st.font.color.rgb = rgb(colors.get("primary"), "C8102E")


def table(doc, columns, rows, style=None, status_of=None):
    style = style or {}
    t = doc.add_table(rows=1, cols=len(columns))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    align = style.get("align") or []
    for i, h in enumerate(columns):
        c = t.rows[0].cells[i]
        c.text = ""
        run = c.paragraphs[0].add_run(h)
        run.bold = True
        shade(c, "EDEDED")
    status_col = style.get("statusColumn")
    for r, row in enumerate(rows):
        cells = t.add_row().cells
        last = style.get("total") and r == len(rows) - 1
        fill = None
        if status_col is not None and status_col < len(row) and status_of:
            fill = ROW_FILL.get(status_of(row[status_col]))
        if fill is None and style.get("banded") and r % 2 == 1:
            fill = "F7F7F7"
        for i, value in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            run = p.add_run(str(value))
            run.bold = bool(last)
            if style.get("alertColumn") == i and _positive(value):
                run.bold = True
                run.font.color.rgb = RGBColor(0x9E, 0x0B, 0x23)
            if i < len(align) and align[i] in ALIGN:
                p.alignment = ALIGN[align[i]]
            if fill:
                shade(cells[i], fill)
    doc.add_paragraph()


def _positive(value):
    try:
        return float(str(value).replace(",", "")) > 0
    except ValueError:
        return False


def word_status(text):
    t = (text or "").strip().lower()
    if t in ("on track", "on target", "done", "complete", "green"):
        return "ON_TARGET"
    if t in ("at risk", "below target", "amber", "delayed"):
        return "BELOW_TARGET"
    if t in ("off track", "off target", "blocked", "red"):
        return "OFF_TARGET"
    return None


def kpis(doc, items, colors):
    status_color = {"ON_TARGET": colors.get("onTarget", "1E7D43"), "BELOW_TARGET": colors.get("belowTarget", "A35D00"),
                    "OFF_TARGET": colors.get("offTarget", "C8102E")}
    t = doc.add_table(rows=1, cols=4)
    t.style = "Table Grid"
    for i, h in enumerate(("Metric", "Value", "Target", "Status")):
        c = t.rows[0].cells[i]
        c.text = ""
        c.paragraphs[0].add_run(h).bold = True
        shade(c, "EDEDED")
    for k in items:
        cells = t.add_row().cells
        cells[0].text = k.get("name") or k.get("key", "")
        if k.get("subtitle"):
            cells[0].add_paragraph(k["subtitle"]).runs[0].italic = True
        cells[1].text = k.get("display") or ("" if k.get("value") is None else str(k["value"]))
        if k.get("deltaLine"):
            cells[1].add_paragraph(k["deltaLine"])
        cells[2].text = k.get("targetLine") or ""
        cells[3].text = ""
        run = cells[3].paragraphs[0].add_run(k.get("statusLabel") or k.get("status") or "")
        run.bold = True
        if k.get("status") in status_color:
            run.font.color.rgb = rgb(status_color[k["status"]], "1A1A1A")
        if k.get("overridden") and k.get("overrideReason"):
            cells[3].add_paragraph("Override: " + k["overrideReason"])
    doc.add_paragraph()


def add_runs(p, runs):
    for r in runs or []:
        run = p.add_run(r.get("text", ""))
        run.bold = bool(r.get("b"))
        run.italic = bool(r.get("i"))
        if r.get("href"):
            run.underline = True
            run.font.color.rgb = RGBColor(0x05, 0x63, 0xC1)
            run.add_text(f" ({r['href']})")


def rich_text(doc, rblocks):
    """Formatted text: paragraphs, bulleted and numbered lists with one sub-level, bold, italic, links."""
    for b in rblocks:
        if b.get("type") == "p":
            add_runs(doc.add_paragraph(), b.get("runs"))
            continue
        top, sub = ("List Number", "List Number 2") if b.get("type") == "ol" else ("List Bullet", "List Bullet 2")
        for item in b.get("items", []):
            add_runs(doc.add_paragraph(style=top), item.get("runs"))
            for s in item.get("items", []):
                add_runs(doc.add_paragraph(style=sub), s.get("runs"))


def blocks(doc, items, colors):
    for b in items:
        kind = b.get("type")
        if kind == "paragraph":
            doc.add_paragraph(b.get("text", ""))
        elif kind == "bullets":
            for i in b.get("items", []):
                doc.add_paragraph(i, style="List Bullet")
        elif kind == "stats":
            p = doc.add_paragraph()
            for s in b.get("items", []):
                p.add_run(s.get("value", "") + " ").bold = True
                p.add_run(s.get("label", "") + "    ")
        elif kind == "table":
            table(doc, b.get("columns", []), b.get("rows", []), b.get("style"), word_status)
        elif kind == "kpis":
            kpis(doc, b.get("items", []), colors)
        elif kind == "chart":
            labels, values = b.get("labels", []), b.get("values", [])
            if labels:
                unit = b.get("unit") or ""
                doc.add_paragraph((b.get("title") or "Trend") + (f" (target {b['target']}{unit})" if b.get("target") is not None else ""),
                                  style="Heading 3")
                table(doc, labels, [[f"{v}{unit}" for v in values]])
        elif kind == "columns":
            t = doc.add_table(rows=1, cols=2)
            for i, side in enumerate(("left", "right")):
                col = b.get(side) or {}
                cell = t.rows[0].cells[i]
                cell.text = ""
                cell.paragraphs[0].add_run(col.get("heading", "")).bold = True
                for item in col.get("items", []):
                    cell.add_paragraph("• " + item)
            doc.add_paragraph()
        elif kind == "richText":
            rich_text(doc, b.get("blocks", []))
        elif kind == "actionItems":
            rows = [[a.get("title", ""), a.get("owner") or "", a.get("team", ""), a.get("due") or "—",
                     a.get("status", "") + (" · overdue" if a.get("overdue") else "")] for a in b.get("items", [])]
            table(doc, ["Action", "Owner", "Team", "Due", "Status"], rows)


def convert(data):
    if data.get("schema") != SCHEMA:
        raise SystemExit(f"Expected schema {SCHEMA}, got {data.get('schema')!r}")
    m = data["meeting"]
    theme = m.get("theme") or {}
    colors = theme.get("colors") or {}
    doc = Document()
    style_document(doc, theme)
    doc.add_heading(m.get("title", "Meeting"), level=0)
    doc.add_paragraph(f"{m.get('dateText', m.get('date', ''))} · {m.get('label', '')}")
    group = None
    for s in data.get("sections", []):
        g = (s.get("group") or {}).get("name")
        if g and g != group:
            doc.add_heading(g, level=1)
        group = g
        doc.add_heading(s["name"], level=2 if g else 1)
        meta = doc.add_paragraph(s.get("team", ""))
        meta.runs[0].italic = True
        if not s.get("blocks"):
            doc.add_paragraph("Not provided yet.").runs[0].italic = True
        blocks(doc, s.get("blocks", []), colors)
        if s.get("notes"):
            p = doc.add_paragraph()
            p.add_run("Notes: ").bold = True
            p.add_run(s["notes"])
        for c in s.get("comments", []):
            p = doc.add_paragraph(style="List Bullet")
            p.add_run(c.get("author", "") + ": ").bold = True
            p.add_run(c.get("body", "") + (" (resolved)" if c.get("resolved") else ""))
            for r in c.get("replies", []):
                doc.add_paragraph(f"↳ {r.get('author', '')}: {r.get('body', '')}", style="List Bullet 2")
    return doc


def main(argv):
    if len(argv) < 2:
        raise SystemExit(__doc__)
    src = Path(argv[1])
    out = Path(argv[2]) if len(argv) > 2 else src.with_suffix(".docx")
    convert(json.loads(src.read_text(encoding="utf-8"))).save(out)
    print(f"Wrote {out}")


if __name__ == "__main__":
    main(sys.argv)
