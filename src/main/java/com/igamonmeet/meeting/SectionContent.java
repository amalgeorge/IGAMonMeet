package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.template.SectionKind;
import org.springframework.http.HttpStatus;

/**
 * Content shapes per Section kind (stored as JSON in meeting_section.content_json):
 * SUMMARY {points:[text], stats:[{value,label}]}; KPI {values:{kpiKey:{value,override,reason}}, commentary};
 * CHART {commentary:[text]}; TABLE {rows:[[cell...]]}; TWO_COLUMN {leftHead, left:[text], rightHead, right:[text]};
 * ACTION_ITEMS keeps its items in action_item (ticket 11).
 */
final class SectionContent {

    static final int MAX_CHARS = 200_000;

    private SectionContent() {
    }

    static void validate(SectionKind kind, int columnCount, JsonNode c) {
        check(c != null && c.isObject(), "Content must be an object.");
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
    static boolean filled(SectionKind kind, String json, long actionItems, ObjectMapper mapper) {
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
                        any |= v.path("value").isNumber();
                    }
                    yield any;
                }
                case TABLE -> c.path("rows").size() > 0;
                case TWO_COLUMN -> hasText(c.path("left")) || hasText(c.path("right"));
                default -> true;
            };
        } catch (Exception e) {
            return false;
        }
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
