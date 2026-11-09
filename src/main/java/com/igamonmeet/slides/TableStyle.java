package com.igamonmeet.slides;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a table looks on slides (docs/spec-v2.md §3.3): column widths as shares, alignment per column, a status column
 * that colours each row, a total row, banded rows. Stored as JSON: on the Template Section for TABLE Sections, on the
 * block for table blocks. Every part is optional.
 */
public record TableStyle(List<Double> widths, List<String> align, Integer statusColumn, boolean total, boolean banded) {

    public static final TableStyle NONE = new TableStyle(null, null, null, false, false);
    private static final List<String> ALIGNS = List.of("left", "center", "right");

    public static TableStyle of(JsonNode n) {
        if (n == null || !n.isObject()) {
            return NONE;
        }
        List<Double> widths = null;
        if (n.path("widths").isArray() && n.get("widths").size() > 0) {
            widths = new ArrayList<>();
            for (JsonNode w : n.get("widths")) widths.add(w.asDouble(0));
        }
        List<String> align = null;
        if (n.path("align").isArray()) {
            align = new ArrayList<>();
            for (JsonNode a : n.get("align")) align.add(ALIGNS.contains(a.asText()) ? a.asText() : "left");
        }
        Integer status = n.path("statusColumn").isInt() ? n.get("statusColumn").asInt() : null;
        return new TableStyle(widths, align, status, n.path("total").asBoolean(false), n.path("banded").asBoolean(false));
    }

    /** Null when the style suits a table with {@code columns} columns, else what is wrong with it. */
    public static String problem(JsonNode n, int columns) {
        if (n == null || n.isNull()) return null;
        if (!n.isObject()) return "The table style must be an object.";
        if (n.has("widths") && !n.get("widths").isNull()) {
            JsonNode w = n.get("widths");
            if (!w.isArray() || w.size() != columns) return "Give one column width per column.";
            for (JsonNode x : w) if (!x.isNumber() || x.asDouble() <= 0) return "Column widths must be positive numbers.";
        }
        if (n.has("align") && !n.get("align").isNull()) {
            JsonNode a = n.get("align");
            if (!a.isArray() || a.size() != columns) return "Give one alignment per column.";
            for (JsonNode x : a) if (!ALIGNS.contains(x.asText())) return "Alignment must be left, center or right.";
        }
        if (n.has("statusColumn") && !n.get("statusColumn").isNull()) {
            JsonNode s = n.get("statusColumn");
            if (!s.isInt() || s.asInt() < 0 || s.asInt() >= columns) return "The status column must be one of the table's columns.";
        }
        for (String b : List.of("total", "banded")) {
            if (n.has(b) && !n.get(b).isNull() && !n.get(b).isBoolean()) return "'" + b + "' must be true or false.";
        }
        return null;
    }

    /** ON_TARGET, BELOW_TARGET or OFF_TARGET for common status words; null for anything else. */
    public static String statusOf(String cell) {
        String v = cell == null ? "" : cell.trim().toLowerCase(Locale.ENGLISH);
        if (v.isEmpty()) return null;
        if (List.of("on track", "on target", "done", "complete", "completed", "green", "ok").contains(v)) return "ON_TARGET";
        if (List.of("at risk", "below target", "amber", "delayed", "watch").contains(v)) return "BELOW_TARGET";
        if (List.of("off track", "off target", "red", "blocked", "late", "failed").contains(v)) return "OFF_TARGET";
        return null;
    }

    /** The total row: the sum of each column whose filled cells are all numbers, "Total" in the first column. */
    public static List<String> totalRow(List<String> head, List<List<String>> rows) {
        List<String> out = new ArrayList<>();
        for (int c = 0; c < head.size(); c++) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean numeric = false;
            boolean ok = true;
            for (List<String> r : rows) {
                String v = c < r.size() ? r.get(c).trim() : "";
                if (v.isEmpty()) continue;
                BigDecimal n = number(v);
                if (n == null) { ok = false; break; }
                sum = sum.add(n);
                numeric = true;
            }
            out.add(ok && numeric && c > 0 ? sum.stripTrailingZeros().toPlainString() : "");
        }
        if (!out.isEmpty() && out.get(0).isEmpty()) out.set(0, "Total");
        return out;
    }

    private static BigDecimal number(String v) {
        try {
            return new BigDecimal(v.replace(",", "").replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
