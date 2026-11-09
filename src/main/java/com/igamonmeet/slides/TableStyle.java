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
 * <p>v3 (docs/spec-v3.md §1.3): whole-number columns (validated on save), an alert column whose cells above 0 show red,
 * and a list column whose cells suggest the Admin's SITREP types.
 */
public record TableStyle(List<Double> widths, List<String> align, Integer statusColumn, boolean total, boolean banded,
                         List<Integer> wholeNumbers, Integer alertColumn, Integer listColumn) {

    public static final TableStyle NONE = new TableStyle(null, null, null, false, false, null, null, null);
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
        List<Integer> whole = null;
        if (n.path("wholeNumbers").isArray()) {
            whole = new ArrayList<>();
            for (JsonNode c : n.get("wholeNumbers")) if (c.isInt()) whole.add(c.asInt());
        }
        Integer alert = n.path("alertColumn").isInt() ? n.get("alertColumn").asInt() : null;
        Integer list = n.path("listColumn").isInt() ? n.get("listColumn").asInt() : null;
        return new TableStyle(widths, align, status, n.path("total").asBoolean(false), n.path("banded").asBoolean(false),
                whole, alert, list);
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
        for (String c : List.of("alertColumn", "listColumn")) {
            if (n.has(c) && !n.get(c).isNull()) {
                JsonNode x = n.get(c);
                if (!x.isInt() || x.asInt() < 0 || x.asInt() >= columns) return "The " + (c.startsWith("alert") ? "alert" : "list") + " column must be one of the table's columns.";
            }
        }
        if (n.has("wholeNumbers") && !n.get("wholeNumbers").isNull()) {
            JsonNode w = n.get("wholeNumbers");
            if (!w.isArray()) return "Whole-number columns must be a list.";
            for (JsonNode x : w) if (!x.isInt() || x.asInt() < 0 || x.asInt() >= columns) return "Whole-number columns must be among the table's columns.";
        }
        for (String b : List.of("total", "banded")) {
            if (n.has(b) && !n.get(b).isNull() && !n.get(b).isBoolean()) return "'" + b + "' must be true or false.";
        }
        return null;
    }

    /** Null when every filled cell of the whole-number columns is a whole number of 0 or more, else the problem. */
    public String cellProblem(List<String> head, List<List<String>> rows) {
        if (wholeNumbers == null) return null;
        for (List<String> r : rows) {
            for (int c : wholeNumbers) {
                String v = c < r.size() && r.get(c) != null ? r.get(c).trim() : "";
                if (!v.isEmpty() && !v.matches("\\d{1,9}")) {
                    return "'" + (c < head.size() ? head.get(c) : "Column " + (c + 1)) + "' takes whole numbers only (found '" + v + "').";
                }
            }
        }
        return null;
    }

    /** True when the alert column's cell in this row is a number above 0. */
    public boolean alert(List<String> row) {
        if (alertColumn == null || alertColumn >= row.size()) return false;
        BigDecimal n = number(row.get(alertColumn) == null ? "" : row.get(alertColumn));
        return n != null && n.signum() > 0;
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
