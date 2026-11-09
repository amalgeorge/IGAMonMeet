package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.template.KpiStatus;
import com.igamonmeet.template.SectionKind;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Content shapes per Section kind (stored as JSON in meeting_section.content_json):
 * SUMMARY {points:[text], stats:[{value,label}]}; KPI {values:{kpiKey:{value,override,reason}}, commentary};
 * CHART {commentary:[text]}; TABLE {rows:[[cell...]]}; TWO_COLUMN {leftHead, left:[text], rightHead, right:[text]};
 * ACTION_ITEMS keeps its items in action_item (ticket 11).
 * Every kind may also carry {notes}: speaker notes shown in Presenter view and exported to PPTX notes.
 */
final class SectionContent {

    static final int MAX_CHARS = 200_000;
    static final int MAX_NOTES = 4000;
    static final int MAX_TABLE_COLUMNS = 12;

    private SectionContent() {
    }

    static void validate(SectionKind kind, int columnCount, java.util.Map<String, com.igamonmeet.template.TemplateKpi> kpis, JsonNode c) {
        check(c != null && c.isObject(), "Content must be an object.");
        check(!c.has("notes") || c.get("notes").isNull() || (c.get("notes").isTextual() && c.get("notes").asText().length() <= MAX_NOTES),
                "Speaker notes must be text of up to " + MAX_NOTES + " characters.");
        switch (kind) {
            case SUMMARY -> {
                textArray(c, "points");
                if (c.has("stats")) {
                    check(c.get("stats").isArray(), "'stats' must be a list.");
                    c.get("stats").forEach(s -> check(s.isObject() && s.path("value").isTextual() && s.path("label").isTextual(), "Each stat needs a value and a label."));
                }
            }
            case KPI -> {
                check(!c.has("values") || c.get("values").isObject(), "'values' must be an object keyed by KPI.");
                check(!c.has("commentary") || c.get("commentary").isTextual(), "'commentary' must be text.");
                c.path("values").fields().forEachRemaining(e -> {
                    com.igamonmeet.template.TemplateKpi def = kpis.get(e.getKey());
                    check(def != null, "Unknown KPI '" + e.getKey() + "'.");
                    JsonNode v = e.getValue();
                    check(v.isObject(), "Each KPI entry must be an object.");
                    checkValue(def, v.get("value"));
                    if (v.hasNonNull("override")) {
                        check(isStatus(v.get("override").asText()), "Unknown status override.");
                        check(v.path("reason").isTextual() && !v.get("reason").asText().isBlank() && v.get("reason").asText().length() <= 300,
                                "A status override needs a reason (up to 300 characters); it is shown on the slide.");
                    }
                });
            }
            case TEXT -> {
                check(c.path("blocks").isArray(), "'blocks' must be a list.");
                check(c.get("blocks").size() <= 200, "A Text Section can hold up to 200 blocks.");
                c.get("blocks").forEach(b -> {
                    String type = b.path("type").asText();
                    switch (type) {
                        case "paragraph" -> check(b.path("text").isTextual() && b.get("text").asText().length() <= 5000,
                                "A paragraph needs text of up to 5000 characters.");
                        case "bullets" -> textArray(b, "items");
                        case "table" -> {
                            // Table blocks: the editor sets the columns, up to 12 (docs/spec-v3.md §1.1).
                            JsonNode cols = b.path("columns");
                            check(cols.isArray() && cols.size() >= 1 && cols.size() <= MAX_TABLE_COLUMNS,
                                    "A table needs 1 to " + MAX_TABLE_COLUMNS + " columns.");
                            cols.forEach(h -> check(h.isTextual() && !h.asText().isBlank() && h.asText().length() <= 100,
                                    "Each column needs a heading of up to 100 characters."));
                            check(b.path("rows").isArray() && b.get("rows").size() <= 500, "A table holds up to 500 rows.");
                            b.get("rows").forEach(r -> {
                                check(r.isArray() && r.size() == cols.size(), "Each table row needs exactly " + cols.size() + " cells.");
                                r.forEach(cell -> check(cell.isTextual() && cell.asText().length() <= 500, "Table cells must be text of up to 500 characters."));
                            });
                            String styleProblem = com.igamonmeet.slides.TableStyle.problem(b.get("style"), cols.size());
                            check(styleProblem == null, styleProblem);
                        }
                        default -> check(false, "Unknown block type '" + type + "'.");
                    }
                });
            }
            case CHART -> textArray(c, "commentary");
            case TABLE -> {
                check(c.path("rows").isArray(), "'rows' must be a list.");
                c.get("rows").forEach(r -> {
                    check(r.isArray() && r.size() == columnCount, "Each row needs exactly " + columnCount + " cells.");
                    r.forEach(cell -> check(cell.isTextual(), "Table cells must be text."));
                });
            }
            case TWO_COLUMN -> {
                textArray(c, "left");
                textArray(c, "right");
            }
            case ACTION_ITEMS -> { }
            default -> throw new IllegalStateException();
        }
    }

    /** True when a Section has the content it needs to count towards progress. */
    static boolean filled(SectionKind kind, String json, long actionItems, Set<String> requiredKpis, ObjectMapper mapper) {
        if (kind == SectionKind.ACTION_ITEMS) {
            return actionItems > 0;
        }
        if (kind == SectionKind.CHART) {
            return true;
        }
        if (json == null) {
            return false;
        }
        try {
            JsonNode c = mapper.readTree(json);
            return switch (kind) {
                case SUMMARY -> hasText(c.path("points"));
                case KPI -> {
                    boolean any = false;
                    for (JsonNode v : c.path("values")) {
                        any |= hasValue(v.path("value"));
                    }
                    boolean allRequired = requiredKpis.stream().allMatch(k -> hasValue(c.path("values").path(k).path("value")));
                    yield any && allRequired;
                }
                case TABLE -> c.path("rows").size() > 0;
                case TWO_COLUMN -> hasText(c.path("left")) || hasText(c.path("right"));
                case TEXT -> !com.igamonmeet.slides.SlidePlanner.textLines(c).isEmpty()
                        || java.util.stream.StreamSupport.stream(c.path("blocks").spliterator(), false)
                                .anyMatch(b -> "table".equals(b.path("type").asText()) && b.path("rows").size() > 0);
                default -> true;
            };
        } catch (Exception e) {
            return false;
        }
    }

    /** A value must fit its KPI's value type (docs/spec-v2.md §2.2). */
    private static void checkValue(com.igamonmeet.template.TemplateKpi def, JsonNode value) {
        if (value == null || value.isNull()) return;
        switch (def.getValueType()) {
            case TEXT_STATUS -> check(value.isTextual() && def.getStatusOptions().stream().anyMatch(o -> o.label().equalsIgnoreCase(value.asText().trim())),
                    "'" + def.getName() + "' must be one of: " + def.getStatusOptions().stream().map(o -> o.label()).collect(java.util.stream.Collectors.joining(", ")) + ".");
            case COUNT -> check(value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0, "'" + def.getName() + "' must be a whole number.");
            case DURATION -> check(value.isNumber() && value.decimalValue().signum() >= 0, "'" + def.getName() + "' must be a duration in minutes.");
            default -> check(value.isNumber(), "KPI values must be numbers.");
        }
    }

    private static boolean isStatus(String s) {
        for (KpiStatus k : KpiStatus.values()) {
            if (k.name().equals(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValue(JsonNode v) {
        return v.isNumber() || (v.isTextual() && !v.asText().isBlank());
    }

    private static boolean hasText(JsonNode arr) {
        for (JsonNode n : arr) {
            if (!n.asText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static void textArray(JsonNode c, String field) {
        check(c.path(field).isArray(), "'" + field + "' must be a list.");
        c.get(field).forEach(n -> check(n.isTextual(), "'" + field + "' must contain text."));
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID_CONTENT", message);
        }
    }
}
