package com.igamonmeet.pptximport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.pptximport.PptxReader.RawSlide;
import com.igamonmeet.template.SectionKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Turns the slides mapped to one Section into that Section's content JSON. Continuation slides are merged. */
@Component
public class ContentExtractor {

    private static final Pattern NUMBER = Pattern.compile("^[−-]?\\d+(?:[.,]\\d+)?");

    private final ObjectMapper json;

    public ContentExtractor(ObjectMapper json) {
        this.json = json;
    }

    public ObjectNode extract(SectionKind kind, List<String> columns, List<KpiView> kpis, List<RawSlide> slides) {
        ObjectNode c = json.createObjectNode();
        switch (kind) {
            case SUMMARY -> {
                ArrayNode points = c.putArray("points");
                slides.forEach(s -> body(s).forEach(points::add));
                c.putArray("stats");
            }
            case TEXT -> {
                // Each body paragraph becomes a paragraph block (the reader does not keep bullet formatting).
                ArrayNode blocks = c.putArray("blocks");
                slides.forEach(s -> body(s).forEach(t -> blocks.addObject().put("type", "paragraph").put("text", t)));
            }
            case TABLE -> {
                ArrayNode rows = c.putArray("rows");
                for (RawSlide s : slides) {
                    tableRows(s, columns).forEach(r -> {
                        ArrayNode row = rows.addArray();
                        r.forEach(row::add);
                    });
                }
            }
            case TWO_COLUMN -> {
                ArrayNode left = c.putArray("left");
                ArrayNode right = c.putArray("right");
                for (RawSlide s : slides) {
                    List<String> l = s.placeholders().get("left");
                    List<String> r = s.placeholders().get("right");
                    if (l == null && r == null && !s.bodyShapes().isEmpty()) {
                        l = s.bodyShapes().get(0);
                        r = s.bodyShapes().size() > 1 ? s.bodyShapes().get(1) : List.of();
                    }
                    (l == null ? List.<String>of() : l).forEach(left::add);
                    (r == null ? List.<String>of() : r).forEach(right::add);
                    first(s.placeholders().get("leftHead")).ifPresent(h -> c.put("leftHead", h));
                    first(s.placeholders().get("rightHead")).ifPresent(h -> c.put("rightHead", h));
                }
                if (!c.has("leftHead")) c.put("leftHead", "Risks");
                if (!c.has("rightHead")) c.put("rightHead", "Decisions needed");
            }
            case KPI -> {
                ObjectNode values = c.putObject("values");
                StringBuilder commentary = new StringBuilder();
                for (RawSlide s : slides) {
                    List<String> texts = s.texts();
                    for (KpiView k : kpis) {
                        int i = indexOfIgnoreCase(texts, k.name());
                        if (i >= 0 && i + 1 < texts.size()) {
                            BigDecimal v = number(texts.get(i + 1));
                            if (v != null) {
                                values.putObject(k.key()).put("value", v);
                            }
                        }
                    }
                    String body = String.join(" ", s.placeholders().getOrDefault("body", List.of()));
                    if (!body.isBlank()) commentary.append(commentary.length() > 0 ? " " : "").append(body);
                }
                c.put("commentary", commentary.toString());
            }
            case CHART -> {
                ArrayNode lines = c.putArray("commentary");
                slides.forEach(s -> s.placeholders().getOrDefault("body", s.bodyShapes().isEmpty() ? List.of()
                        : s.bodyShapes().get(s.bodyShapes().size() - 1)).forEach(lines::add));
            }
            case ACTION_ITEMS -> { }
            default -> { }
        }
        String notes = slides.stream().map(RawSlide::notes).filter(n -> !n.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
        if (!notes.isBlank()) {
            c.put("notes", notes.length() > 4000 ? notes.substring(0, 4000) : notes);
        }
        return c;
    }

    /** Rows for an Action items Section: title, owner, team, due text. */
    public List<List<String>> actionRows(List<RawSlide> slides) {
        List<List<String>> out = new ArrayList<>();
        for (RawSlide s : slides) {
            out.addAll(tableRows(s, List.of("Action", "Owner", "Team", "Due")));
        }
        return out;
    }

    /** Body paragraphs: the 'body' placeholder when the slide uses the House template, otherwise every text shape. */
    private static List<String> body(RawSlide s) {
        if (s.placeholders().containsKey("body")) {
            return s.placeholders().get("body");
        }
        List<String> out = new ArrayList<>();
        s.bodyShapes().forEach(out::addAll);
        return out;
    }

    /** Maps table columns by header name (case-insensitive), else by position; header row and repeated headers dropped. */
    private static List<List<String>> tableRows(RawSlide s, List<String> columns) {
        List<List<String>> out = new ArrayList<>();
        if (s.table().isEmpty()) {
            body(s).forEach(p -> {
                List<String> row = new ArrayList<>();
                for (int i = 0; i < columns.size(); i++) row.add(i == 0 ? p : "");
                out.add(row);
            });
            return out;
        }
        List<String> header = s.table().get(0);
        int[] from = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            int found = indexOfIgnoreCase(header, columns.get(i));
            from[i] = found >= 0 ? found : i;
        }
        for (List<String> r : s.table().subList(1, s.table().size())) {
            if (r.equals(header) || r.stream().allMatch(String::isBlank)) continue;
            List<String> row = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) row.add(from[i] < r.size() ? r.get(from[i]) : "");
            out.add(row);
        }
        return out;
    }

    private static int indexOfIgnoreCase(List<String> list, String value) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).trim().equalsIgnoreCase(value.trim())) return i;
        }
        return -1;
    }

    private static BigDecimal number(String text) {
        Matcher m = NUMBER.matcher(text.trim());
        return m.find() ? new BigDecimal(m.group().replace('−', '-').replace(',', '.')) : null;
    }

    private static java.util.Optional<String> first(List<String> l) {
        return l == null || l.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(l.get(0));
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
