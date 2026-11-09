package com.igamonmeet.export;

import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.slides.PlanModel.Plan;
import com.igamonmeet.slides.PlanModel.Slide;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.sl.usermodel.AutoNumberingScheme;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFChart;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

/**
 * Writes a Slide plan into a copy of the House template: one slide per plan slide on its named layout,
 * placeholders filled by name (ADR 0005); tiles, tables and charts drawn on top. Native, editable PowerPoint.
 */
@Component
public class PptxWriter {

    private static final Color INK = new Color(0x1A, 0x1A, 0x1A);
    private static final Color MUTED = new Color(0x5C, 0x5C, 0x5C);
    private static final Color SOFT = new Color(0xF6, 0xF6, 0xF7);
    private static final Color LINE = new Color(0xE3, 0xE3, 0xE3);
    private static final Map<String, Color[]> STATUS = Map.of(
            "ON_TARGET", new Color[] {new Color(0xE3, 0xF3, 0xE9), new Color(0x17, 0x64, 0x3A)},
            "BELOW_TARGET", new Color[] {new Color(0xFF, 0xF3, 0xDC), new Color(0x7A, 0x45, 0x00)},
            "OFF_TARGET", new Color[] {new Color(0xFD, 0xEC, 0xEE), new Color(0x9E, 0x0B, 0x23)});
    private static final Map<String, String> STATUS_LABEL = Map.of("ON_TARGET", "On target", "BELOW_TARGET", "Below target", "OFF_TARGET", "Off target");

    public record Options(boolean notes, String footerStamp, List<List<String>> commentRows) {
    }

    public byte[] write(byte[] houseTemplate, Plan plan, Options options) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(houseTemplate)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            while (!ppt.getSlides().isEmpty()) {
                ppt.removeSlide(0);
            }
            removeOrphanedChartParts(ppt);
            double w = ppt.getPageSize().getWidth();
            double h = ppt.getPageSize().getHeight();
            List<Slide> slides = new ArrayList<>(plan.slides());
            for (Slide s : slides) {
                XSLFSlide slide = ppt.createSlide(layout(ppt, s.layout()));
                fill(ppt, slide, s, w, h);
                if (options.notes() && s.data().get("notes") instanceof String n && !n.isBlank()) {
                    notes(ppt, slide, n);
                }
                stamp(slide, options.footerStamp(), w, h);
            }
            if (options.commentRows() != null && !options.commentRows().isEmpty()) {
                int perSlide = 8;
                int pages = (options.commentRows().size() + perSlide - 1) / perSlide;
                for (int p = 0; p < pages; p++) {
                    XSLFSlide slide = ppt.createSlide(layout(ppt, "IAM_TABLE"));
                    text(slide, "team", "APPENDIX");
                    text(slide, "title", "Comments" + (pages > 1 ? " (" + (p + 1) + " of " + pages + ")" : ""));
                    table(slide, List.of("Section", "Comment", "Status"), options.commentRows().subList(p * perSlide,
                            Math.min(options.commentRows().size(), (p + 1) * perSlide)), List.of(20.0, 62.0, 18.0), null, null, false, w, h);
                    stamp(slide, options.footerStamp(), w, h);
                }
            }
            ppt.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Removing the template's sample slides leaves their charts and embedded workbooks in the package;
     * POI would then reuse the same part names for new charts and fail. Layouts and masters never hold charts.
     */
    private static void removeOrphanedChartParts(XMLSlideShow ppt) {
        var pkg = ppt.getPackage();
        for (PackagePart part : pkg.getPartsByName(Pattern.compile("/ppt/(charts|embeddings)/.*"))) {
            // Removing a part also removes its _rels part, which may still be in this list.
            if (pkg.containPart(part.getPartName())) {
                pkg.removePart(part.getPartName());
            }
        }
    }

    private static XSLFSlideLayout layout(XMLSlideShow ppt, String name) {
        return ppt.getSlideMasters().stream().map(m -> m.getLayout(name)).filter(l -> l != null).findFirst()
                .orElseThrow(() -> new IllegalStateException("House template has no layout " + name));
    }

    @SuppressWarnings("unchecked")
    private void fill(XMLSlideShow ppt, XSLFSlide slide, Slide s, double w, double h) throws IOException {
        Map<String, Object> d = s.data();
        for (String key : List.of("title", "subtitle", "meta", "team", "leftHead", "rightHead")) {
            if (d.get(key) instanceof String v) {
                text(slide, key, v);
            }
        }
        if (d.get("empty") instanceof String empty) {
            box(slide, empty, 0.045 * w, 0.267 * h, 0.91 * w, 0.1 * h, 18, false, MUTED);
            return;
        }
        switch (s.layout()) {
            case "IAM_SECTION", "IAM_CLOSING" -> text(slide, "body", String.valueOf(d.getOrDefault("body", "")));
            case "IAM_SUMMARY" -> {
                if (d.containsKey("blocks")) {
                    blocks(slide, (List<Map<String, Object>>) d.get("blocks"), ((Number) d.get("fontSize")).doubleValue(), w);
                    break;
                }
                list(slide, "body", (List<String>) d.get("points"), ((Number) d.get("fontSize")).doubleValue(), true);
                List<Map<String, Object>> stats = (List<Map<String, Object>>) d.getOrDefault("stats", List.of());
                for (int i = 0; i < stats.size(); i++) {
                    double y = 0.267 * h + i * 0.207 * h;
                    tile(slide, 0.63 * w, y, 0.325 * w, 0.18 * h);
                    box(slide, String.valueOf(stats.get(i).get("value")), 0.65 * w, y + 6, 0.29 * w, 40, 36, true, INK);
                    box(slide, String.valueOf(stats.get(i).get("label")), 0.65 * w, y + 50, 0.29 * w, 24, 13, false, MUTED);
                }
            }
            case "IAM_TWO_COLUMN" -> {
                double size = ((Number) d.get("fontSize")).doubleValue();
                list(slide, "left", (List<String>) d.get("left"), size, false);
                list(slide, "right", (List<String>) d.get("right"), size, false);
            }
            case "IAM_KPI" -> {
                kpis(slide, (List<KpiView>) d.get("kpis"), d.get("perSlide") instanceof Integer n ? n : 4, w, h);
                text(slide, "body", String.valueOf(d.getOrDefault("body", "")));
            }
            case "IAM_CHART" -> {
                chart(ppt, slide, d, w, h);
                list(slide, "body", (List<String>) d.getOrDefault("commentary", List.of()), 16, false);
            }
            case "IAM_TABLE" -> {
                table(slide, (List<String>) d.get("head"), (List<List<String>>) d.get("rows"), (List<Double>) d.get("colWidths"),
                        (List<String>) d.get("rowFlags"), (List<String>) d.get("align"), Boolean.TRUE.equals(d.get("banded")), w, h);
                if (d.get("note") instanceof String note) {
                    box(slide, note, 0.045 * w, 0.84 * h, 0.91 * w, 20, 12, false, MUTED);
                }
            }
            default -> { }
        }
        if (d.get("note") instanceof String note && "IAM_TWO_COLUMN".equals(s.layout())) {
            box(slide, note, 0.045 * w, 0.84 * h, 0.91 * w, 20, 12, false, MUTED);
        }
    }

    // ---- placeholders ----

    private static XSLFTextShape placeholder(XSLFSlide slide, String name) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape t && name.equals(shape.getShapeName())) {
                return t;
            }
        }
        return null;
    }

    private static void text(XSLFSlide slide, String name, String value) {
        XSLFTextShape t = placeholder(slide, name);
        if (t != null) {
            t.clearText();
            t.setText(value);
        }
    }

/** A Text Section's blocks in the body placeholder, widened to the full content width: paragraphs and bullet items. */
    private static void blocks(XSLFSlide slide, List<Map<String, Object>> blocks, double size, double w) {
        XSLFTextShape t = placeholder(slide, "body");
        if (t == null) {
            return;
        }
        Rectangle2D a = t.getAnchor();
        t.setAnchor(new Rectangle2D.Double(0.045 * w, a.getY(), 0.91 * w, a.getHeight()));
        t.clearText();
        for (Map<String, Object> b : blocks) {
            XSLFTextParagraph p = t.addNewTextParagraph();
            boolean li = "li".equals(b.get("type"));
            p.setBullet(li);
            if (!li) {
                p.setIndentLevel(0);
                p.setIndent(0.0);
                p.setLeftMargin(0.0);
            }
            p.setSpaceAfter(li ? 6.0 : 12.0);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(String.valueOf(b.get("text")));
            r.setFontSize(size);
        }
    }

        private static void list(XSLFSlide slide, String name, List<String> items, double size, boolean numbered) {
        XSLFTextShape t = placeholder(slide, name);
        if (t == null || items == null) {
            return;
        }
        t.clearText();
        for (String item : items) {
            XSLFTextParagraph p = t.addNewTextParagraph();
            if (numbered) {
                p.setBulletAutoNumber(AutoNumberingScheme.arabicPeriod, 1);
            } else {
                p.setBullet(true);
            }
            p.setSpaceAfter(10.0);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(item);
            r.setFontSize(size);
        }
    }

    // ---- composed shapes ----

    private static void tile(XSLFSlide slide, double x, double y, double w, double h) {
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(ShapeType.ROUND_RECT);
        s.setAnchor(new Rectangle2D.Double(x, y, w, h));
        s.setFillColor(SOFT);
        s.setLineColor(SOFT);
    }

    private static XSLFTextBox box(XSLFSlide slide, String text, double x, double y, double w, double h, double size, boolean bold, Color color) {
        XSLFTextBox b = slide.createTextBox();
        b.setAnchor(new Rectangle2D.Double(x, y, w, h));
        b.setInsets(new org.apache.poi.sl.usermodel.Insets2D(0, 0, 0, 0));
        b.setVerticalAlignment(VerticalAlignment.TOP);
        XSLFTextRun r = b.addNewTextParagraph().addNewTextRun();
        r.setText(text);
        r.setFontSize(size);
        r.setBold(bold);
        r.setFontColor(color);
        return b;
    }

    /** Tiles in one row (2 to 4 per slide) or two rows (6 or 8), compact in two rows (spec-v2 §2.5). */
    private static void kpis(XSLFSlide slide, List<KpiView> kpis, int perSlide, double w, double h) {
        double gap = 21.6;
        int cols = perSlide <= 4 ? perSlide : perSlide / 2;
        boolean compact = perSlide > 4;
        double tw = (0.91 * w - (cols - 1) * gap) / cols;
        double top = 0.267 * h;
        double th = compact ? (0.48 * h - gap) / 2 : 0.453 * h;
        for (int i = 0; i < kpis.size(); i++) {
            KpiView k = kpis.get(i);
            double x = 0.045 * w + (i % cols) * (tw + gap);
            double y = top + (i / cols) * (th + gap);
            tile(slide, x, y, tw, th);
            box(slide, k.name(), x + 14, y + (compact ? 8 : 18), tw - 28, compact ? 30 : 40, compact ? 11 : 14, false, MUTED);
            // Formatted by value type on the server (KpiFormat), the same text as the in-app slide.
            int big = compact ? (k.display().length() > 9 ? 18 : 24) : (k.display().length() > 9 ? 28 : 40);
            box(slide, k.display(), x + 14, y + (compact ? 36 : 64), tw - 28, compact ? 32 : 50, big, true, INK);
            String target = k.targetLine() == null ? "" : k.targetLine();
            if (k.deltaLine() != null && !compact) {
                target += (target.isEmpty() ? "" : "\n") + k.deltaLine();
            }
            box(slide, target, x + 14, y + (compact ? 70 : 120), tw - 28, compact ? 20 : 40, compact ? 10 : 12, false, MUTED);
            if (k.status() != null) {
                Color[] c = STATUS.get(k.status().name());
                XSLFAutoShape pill = slide.createAutoShape();
                pill.setShapeType(ShapeType.ROUND_RECT);
                pill.setAnchor(new Rectangle2D.Double(x + 14, y + th - (compact ? 30 : 44), compact ? 92 : 110, compact ? 20 : 26));
                pill.setFillColor(c[0]);
                pill.setLineColor(c[0]);
                pill.setVerticalAlignment(VerticalAlignment.MIDDLE);
                XSLFTextParagraph p = pill.addNewTextParagraph();
                p.setTextAlign(TextAlign.CENTER);
                XSLFTextRun r = p.addNewTextRun();
                r.setText(k.statusLabel() != null ? k.statusLabel() : STATUS_LABEL.get(k.status().name()));
                r.setFontSize(compact ? 9.0 : 11.0);
                r.setBold(true);
                r.setFontColor(c[1]);
            }
            if (!compact && k.override() != null && k.reason() != null) {
                box(slide, k.reason(), x + 14, y + th + 6, tw - 36, 30, 10, false, MUTED);
            }
        }
    }

    /** Row fills for status-coloured rows (spec-v2 §3.3); the text stays dark for contrast. */
    private static final Map<String, Color> ROW_FILL = Map.of("ON_TARGET", new Color(0xE3, 0xF3, 0xE9), "BELOW_TARGET", new Color(0xFF, 0xF3, 0xDC),
            "OFF_TARGET", new Color(0xFD, 0xEC, 0xEE));
    private static final Color BAND = new Color(0xF7, 0xF7, 0xF7);

    private static void table(XSLFSlide slide, List<String> head, List<List<String>> rows, List<Double> widths, List<String> flags,
                              List<String> align, boolean banded, double w, double h) {
        XSLFTable t = slide.createTable();
        double x = 0.045 * w;
        double tw = 0.91 * w;
        t.setAnchor(new Rectangle2D.Double(x, 0.267 * h, tw, 0.55 * h));
        XSLFTableRow hr = t.addRow();
        for (int c = 0; c < head.size(); c++) {
            XSLFTableCell hc = hr.addCell();
            cell(hc, head.get(c), 12, true, MUTED, SOFT);
            if (align != null && c < align.size() && !"left".equals(align.get(c))) {
                hc.getTextParagraphs().get(0).setTextAlign("center".equals(align.get(c)) ? TextAlign.CENTER : TextAlign.RIGHT);
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            XSLFTableRow tr = t.addRow();
            String flag = flags == null ? "" : flags.get(r);
            List<String> row = rows.get(r);
            for (int c = 0; c < head.size(); c++) {
                boolean late = "overdue".equals(flag) && c == head.size() - 1;
                Color color = "closed".equals(flag) ? MUTED : late ? new Color(0x9E, 0x0B, 0x23) : INK;
                XSLFTableCell cell = tr.addCell();
                boolean total = "total".equals(flag);
                Color fill = flag.startsWith("status:") ? ROW_FILL.get(flag.substring(7)) : total ? SOFT : banded && r % 2 == 1 ? BAND : null;
                cell(cell, c < row.size() ? row.get(c) : "", 14, late || total, color, fill);
                if (align != null && c < align.size()) {
                    cell.getTextParagraphs().get(0).setTextAlign("center".equals(align.get(c)) ? TextAlign.CENTER
                            : "right".equals(align.get(c)) ? TextAlign.RIGHT : TextAlign.LEFT);
                }
                if ("closed".equals(flag)) {
                    cell.getTextParagraphs().get(0).getTextRuns().get(0).setStrikethrough(true);
                }
            }
        }
        for (int c = 0; c < head.size(); c++) {
            double pct = widths != null && c < widths.size() ? widths.get(c) : 100.0 / head.size();
            t.setColumnWidth(c, tw * pct / 100);
        }
    }

    private static void cell(XSLFTableCell cell, String text, double size, boolean bold, Color color, Color fill) {
        cell.clearText();
        XSLFTextRun r = cell.addNewTextParagraph().addNewTextRun();
        r.setText(text);
        r.setFontSize(size);
        r.setBold(bold);
        r.setFontColor(color);
        if (fill != null) {
            cell.setFillColor(fill);
        }
        for (org.apache.poi.sl.usermodel.TableCell.BorderEdge e : org.apache.poi.sl.usermodel.TableCell.BorderEdge.values()) {
            cell.setBorderColor(e, LINE);
            cell.setBorderWidth(e, 1.0);
        }
        cell.setInsets(new org.apache.poi.sl.usermodel.Insets2D(5.75, 10.8, 5.75, 10.8));
    }

    @SuppressWarnings("unchecked")
    private static void chart(XMLSlideShow ppt, XSLFSlide slide, Map<String, Object> d, double w, double h) {
        List<String> labels = (List<String>) d.getOrDefault("labels", List.of());
        List<?> values = (List<?>) d.getOrDefault("values", List.of());
        if (labels.isEmpty()) {
            box(slide, "No values yet.", 0.045 * w, 0.267 * h, 0.6 * w, 30, 16, false, MUTED);
            return;
        }
        Double[] nums = values.stream().map(v -> ((Number) v).doubleValue()).toArray(Double[]::new);
        XSLFChart chart = ppt.createChart();
        XDDFCategoryAxis bottom = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis left = chart.createValueAxis(AxisPosition.LEFT);
        XDDFCategoryDataSource cats = XDDFDataSourcesFactory.fromArray(labels.toArray(new String[0]));
        XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromArray(nums);
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottom, left);
        XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(cats, vals);
        series.setTitle(String.valueOf(d.getOrDefault("title", "")), null);
        series.setSmooth(false);
        series.setMarkerStyle(MarkerStyle.CIRCLE);
        XDDFLineProperties line = new XDDFLineProperties();
        line.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(new byte[] {(byte) 0xC8, 0x10, 0x2E})));
        line.setWidth(2.5);
        XDDFShapeProperties props = new XDDFShapeProperties();
        props.setLineProperties(line);
        series.setShapeProperties(props);
        chart.plot(data);
        slide.addChart(chart, new Rectangle2D.Double(0.045 * w, 0.267 * h, 0.6 * w, 0.613 * h));
    }

    // ---- notes and stamp ----

    private static void notes(XMLSlideShow ppt, XSLFSlide slide, String text) {
        XSLFNotes notes = ppt.getNotesSlide(slide);
        for (XSLFTextShape t : notes.getPlaceholders()) {
            if (t.getTextType() == Placeholder.BODY) {
                t.setText(text);
                return;
            }
        }
    }

    private static void stamp(XSLFSlide slide, String stamp, double w, double h) {
        if (stamp != null && !stamp.isBlank()) {
            box(slide, stamp, 0.38 * w, 0.927 * h, 0.4 * w, 16, 9, false, MUTED);
        }
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
