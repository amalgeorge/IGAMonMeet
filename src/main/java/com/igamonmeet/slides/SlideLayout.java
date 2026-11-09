package com.igamonmeet.slides;

import com.fasterxml.jackson.databind.JsonNode;
import com.igamonmeet.template.SectionKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin's slide layout for a Section (docs/spec-v3.md §6). Boxes on a 12 x 8 grid of the 16:9 slide:
 * <pre>
 * {"title": {"x":0,"y":0,"w":12,"h":1}, "content": {...}, "chart": {...}, "left": {...}, "right": {...},
 *  "tiles": [{...}, ...]}       KPI Sections: one box per tile on a slide
 * </pre>
 * Every box is optional; a missing one keeps the generated position. On slides the boxes become a "frame" of
 * percentages of the slide, which the in-app renderer and the PPTX writer both place shapes in.
 */
public final class SlideLayout {

    public static final int COLS = 12;
    public static final int ROWS = 8;

    /** A box as fractions of the slide (0..1). */
    public record Rect(double x, double y, double w, double h) {

        public Map<String, Object> percent() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", round(x * 100));
            m.put("y", round(y * 100));
            m.put("w", round(w * 100));
            m.put("h", round(h * 100));
            return m;
        }

        private static double round(double v) {
            return Math.round(v * 100) / 100.0;
        }
    }

    /** The boxes of one Section; any may be null. tiles is empty when not set. */
    public record Frame(Rect title, Rect content, Rect chart, Rect left, Rect right, List<Rect> tiles) {

        public Map<String, Object> data() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (title != null) m.put("title", title.percent());
            if (content != null) m.put("content", content.percent());
            if (chart != null) m.put("chart", chart.percent());
            if (left != null) m.put("left", left.percent());
            if (right != null) m.put("right", right.percent());
            if (!tiles.isEmpty()) m.put("tiles", tiles.stream().map(Rect::percent).toList());
            return m;
        }
    }

    private SlideLayout() {
    }

    /** Which boxes a kind of Section can arrange. */
    public static Set<String> items(SectionKind kind) {
        return switch (kind) {
            case KPI -> Set.of("title", "tiles");
            case CHART -> Set.of("title", "chart", "content");
            case TWO_COLUMN -> Set.of("title", "left", "right");
            default -> Set.of("title", "content");
        };
    }

    /** Null when {@code layout} is valid for {@code kind} (or absent), else what is wrong. */
    public static String problem(JsonNode layout, SectionKind kind) {
        if (layout == null || layout.isNull()) return null;
        if (!layout.isObject()) return "the slide layout must be an object.";
        Set<String> allowed = items(kind);
        var names = layout.fieldNames();
        while (names.hasNext()) {
            String n = names.next();
            if (!allowed.contains(n)) return "'" + n + "' cannot be arranged on this kind of Section.";
            JsonNode v = layout.get(n);
            if (n.equals("tiles")) {
                if (!v.isArray() || v.size() < 1 || v.size() > 8) return "give 1 to 8 KPI tile boxes.";
                for (JsonNode t : v) {
                    String p = boxProblem(t);
                    if (p != null) return "tile: " + p;
                }
            } else {
                String p = boxProblem(v);
                if (p != null) return n + ": " + p;
            }
        }
        return null;
    }

    private static String boxProblem(JsonNode b) {
        if (!b.isObject()) return "each box needs x, y, w and h.";
        for (String f : List.of("x", "y", "w", "h")) {
            if (!b.path(f).isInt()) return "each box needs whole-number x, y, w and h.";
        }
        int x = b.get("x").asInt(), y = b.get("y").asInt(), w = b.get("w").asInt(), h = b.get("h").asInt();
        if (x < 0 || y < 0 || w < 1 || h < 1 || x + w > COLS || y + h > ROWS) return "boxes must stay on the 12 x 8 grid.";
        return null;
    }

    /** The frame for a Section, or null when it has no layout. */
    public static Frame frame(JsonNode layout) {
        if (layout == null || !layout.isObject() || layout.isEmpty()) return null;
        List<Rect> tiles = new ArrayList<>();
        layout.path("tiles").forEach(t -> tiles.add(rect(t)));
        return new Frame(rect(layout.get("title")), rect(layout.get("content")), rect(layout.get("chart")),
                rect(layout.get("left")), rect(layout.get("right")), tiles);
    }

    private static Rect rect(JsonNode b) {
        if (b == null || !b.isObject()) return null;
        return new Rect(b.path("x").asDouble() / COLS, b.path("y").asDouble() / ROWS, b.path("w").asDouble() / COLS, b.path("h").asDouble() / ROWS);
    }
}
