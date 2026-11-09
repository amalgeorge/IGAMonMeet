package com.igamonmeet.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.meeting.Comment;
import com.igamonmeet.meeting.CommentRepository;
import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.meeting.Meeting;
import com.igamonmeet.meeting.MeetingController.ActionView;
import com.igamonmeet.meeting.MeetingController.Detail;
import com.igamonmeet.meeting.MeetingController.SectionView;
import com.igamonmeet.meeting.MeetingDetails;
import com.igamonmeet.meeting.MeetingService;
import com.igamonmeet.meeting.MeetingTime;
import com.igamonmeet.slides.PlanModel.Series;
import com.igamonmeet.slides.SlidePlanService;
import com.igamonmeet.slides.TableStyle;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import com.igamonmeet.theme.MeetingThemes;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Meeting as a document-shaped JSON file (docs/json-export.md, schema "igamonmeet.document/1"): Sections in order,
 * each as a list of blocks a Word converter can walk top to bottom without knowing Section kinds.
 */
@Service
public class DocumentExport {

    public static final String SCHEMA = "igamonmeet.document/1";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

    private final MeetingDetails details;
    private final MeetingService meetings;
    private final SlidePlanService plans;
    private final MeetingThemes themes;
    private final CommentRepository comments;
    private final MeetingTime time;
    private final AuditLog audit;
    private final ObjectMapper json;

    public DocumentExport(MeetingDetails details, MeetingService meetings, SlidePlanService plans, MeetingThemes themes,
                          CommentRepository comments, MeetingTime time, AuditLog audit, ObjectMapper json) {
        this.details = details;
        this.meetings = meetings;
        this.plans = plans;
        this.themes = themes;
        this.comments = comments;
        this.time = time;
        this.audit = audit;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ExportService.File json(long meetingId, ExportService.Request r, String user) {
        Meeting m = meetings.live(meetingId);
        Detail d = details.detail(meetingId, true);
        Set<String> include = r == null || r.sections() == null ? null : Set.copyOf(r.sections());
        boolean withNotes = r == null || r.notes();
        boolean withComments = r != null && r.comments();

        ObjectNode doc = json.createObjectNode();
        doc.put("schema", SCHEMA);
        doc.put("exportedAt", time.now().toString());
        doc.put("exportedBy", user);

        ObjectNode meeting = doc.putObject("meeting");
        meeting.put("id", m.getId());
        meeting.put("title", m.getTitle());
        meeting.put("month", m.getMonth());
        meeting.put("monthText", MONTH.format(YearMonth.parse(m.getMonth())));
        meeting.put("date", m.getMeetingDate().toString());
        meeting.put("dateText", LONG_DATE.format(m.getMeetingDate()));
        meeting.put("label", d.meeting().label());
        meeting.put("templateVersion", m.getTemplateVersionNo());
        meeting.put("requiredSections", d.requiredSections());
        meeting.put("requiredFilled", d.requiredFilled());
        meeting.set("theme", theme(meetingId));

        Map<String, List<Comment>> bySection = new HashMap<>();
        List<Comment> all = withComments ? comments.findByMeetingIdOrderByCreatedAtAscIdAsc(meetingId) : List.of();
        all.forEach(c -> bySection.computeIfAbsent(c.getSectionKey(), k -> new java.util.ArrayList<>()).add(c));

        ArrayNode sections = doc.putArray("sections");
        int exported = 0;
        for (SectionView s : d.sections()) {
            if (include != null && !include.contains(s.key())) { // hidden Sections are already left out of the detail
                continue;
            }
            exported++;
            ObjectNode o = sections.addObject();
            o.put("key", s.key());
            o.put("name", s.name());
            o.put("team", team(s.teamTag()));
            o.put("kind", s.kind().name());
            if (s.groupKey() != null) {
                o.putObject("group").put("key", s.groupKey()).put("name", s.groupName());
            } else {
                o.putNull("group");
            }
            o.put("required", s.required());
            o.put("filled", s.filled());
            o.put("onSlides", s.onSlides());
            o.put("updatedBy", s.updatedBy());
            o.put("updatedAt", s.updatedAt() == null ? null : s.updatedAt().toString());
            JsonNode c = read(s.contentJson());
            o.set("blocks", blocks(m, s, c, d.actionItems()));
            if (withNotes && c.path("notes").isTextual() && !c.get("notes").asText().isBlank()) {
                o.put("notes", c.get("notes").asText());
            }
            if (withComments) {
                o.set("comments", comments(bySection.getOrDefault(s.key(), List.of())));
            }
        }
        ArrayNode actions = doc.putArray("actionItems");
        d.actionItems().forEach(a -> actions.add(action(a)));

        audit.record("EXPORTED", "Meeting", meetingId, "JSON (" + SCHEMA + "), " + exported + " sections");
        String name = m.getTitle().replaceAll("[^A-Za-z0-9 ._-]+", " ").trim().replaceAll("\\s+", "_") + ".json";
        try {
            return new ExportService.File(name, json.writerWithDefaultPrettyPrinter().writeValueAsString(doc).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Every Section kind becomes the same small set of block types (docs/json-export.md §3). */
    private ArrayNode blocks(Meeting m, SectionView s, JsonNode c, List<ActionView> actionItems) {
        ArrayNode out = json.createArrayNode();
        switch (s.kind()) {
            case SUMMARY -> {
                if (c.path("stats").size() > 0) {
                    ArrayNode items = out.addObject().put("type", "stats").putArray("items");
                    c.get("stats").forEach(st -> items.addObject().put("value", st.path("value").asText()).put("label", st.path("label").asText()));
                }
                bullets(out, c.path("points"));
            }
            case KPI -> {
                ObjectNode b = out.addObject().put("type", "kpis");
                ArrayNode items = b.putArray("items");
                s.kpis().forEach(k -> items.add(kpi(k)));
                paragraph(out, c.path("commentary").asText(""));
            }
            case CHART -> {
                ObjectNode b = out.addObject().put("type", "chart");
                KpiView def = s.kpis().stream().filter(k -> k.key().equals(s.chartKpiKey())).findFirst().orElse(null);
                b.put("kpiKey", s.chartKpiKey());
                if (s.chartKpiKey() != null) {
                    Series series = plans.series(m, s.chartKpiKey(), def);
                    b.put("title", def == null ? s.name() : def.name());
                    b.put("unit", series.unit());
                    if (series.target() != null) b.put("target", series.target());
                    else b.putNull("target");
                    ArrayNode labels = b.putArray("labels");
                    series.labels().forEach(labels::add);
                    ArrayNode values = b.putArray("values");
                    series.values().forEach(values::add);
                }
                bullets(out, c.path("commentary"));
            }
            case TABLE -> table(out, s.columns(), c.path("rows"), s.tableStyle() == null ? null : read(s.tableStyle()));
            case TWO_COLUMN -> {
                ObjectNode b = out.addObject().put("type", "columns");
                column(b.putObject("left"), c.path("leftHead").asText(""), c.path("left"));
                column(b.putObject("right"), c.path("rightHead").asText(""), c.path("right"));
            }
            case TEXT -> c.path("blocks").forEach(b -> {
                switch (b.path("type").asText()) {
                    case "paragraph" -> paragraph(out, b.path("text").asText(""));
                    case "bullets" -> bullets(out, b.path("items"));
                    case "table" -> {
                        List<String> cols = new java.util.ArrayList<>();
                        b.path("columns").forEach(h -> cols.add(h.asText()));
                        table(out, cols, b.path("rows"), b.get("style"));
                    }
                    default -> { }
                }
            });
            case ACTION_ITEMS -> {
                ArrayNode items = out.addObject().put("type", "actionItems").putArray("items");
                actionItems.forEach(a -> items.add(action(a)));
            }
            default -> { }
        }
        return out;
    }

    private void paragraph(ArrayNode out, String text) {
        if (!text.isBlank()) {
            out.addObject().put("type", "paragraph").put("text", text);
        }
    }

    private void bullets(ArrayNode out, JsonNode items) {
        ArrayNode list = json.createArrayNode();
        items.forEach(i -> {
            if (i.isTextual() && !i.asText().isBlank()) list.add(i.asText());
        });
        if (!list.isEmpty()) {
            out.addObject().put("type", "bullets").set("items", list);
        }
    }

    private void column(ObjectNode o, String heading, JsonNode items) {
        o.put("heading", heading);
        ArrayNode list = o.putArray("items");
        items.forEach(i -> list.add(i.asText()));
    }

    /** Tables carry their style resolved: widths in percent, alignment, status column, total row, banding. */
    private void table(ArrayNode out, List<String> columns, JsonNode rows, JsonNode style) {
        ObjectNode b = out.addObject().put("type", "table");
        ArrayNode cols = b.putArray("columns");
        columns.forEach(cols::add);
        ArrayNode rs = b.putArray("rows");
        rows.forEach(r -> {
            ArrayNode row = rs.addArray();
            r.forEach(cell -> row.add(cell.asText()));
        });
        TableStyle st = style == null || style.isNull() ? TableStyle.NONE : TableStyle.of(style);
        if (st.total() && rs.size() > 0) {
            // The computed total row, as on the slides (TableStyle.totalRow), so converters just bold the last row.
            List<List<String>> plain = new java.util.ArrayList<>();
            rs.forEach(r -> {
                List<String> row = new java.util.ArrayList<>();
                r.forEach(c -> row.add(c.asText()));
                plain.add(row);
            });
            ArrayNode total = rs.addArray();
            TableStyle.totalRow(columns, plain).forEach(total::add);
        }
        b.set("style", json.valueToTree(st));
    }

    private ObjectNode kpi(KpiView k) {
        ObjectNode o = json.createObjectNode();
        o.put("key", k.key());
        o.put("name", k.name());
        o.put("subtitle", k.subtitle());
        o.put("description", k.description());
        o.put("valueType", k.valueType() == null ? null : k.valueType().name());
        if (k.value() != null) o.put("value", k.value());
        else if (k.text() != null) o.put("value", k.text());
        else o.putNull("value");
        o.put("unit", k.unit());
        o.put("display", k.display());
        if (k.target() != null) o.put("target", k.target());
        else o.putNull("target");
        o.put("targetLine", k.targetLine());
        o.put("deltaLine", k.deltaLine());
        o.put("status", k.status() == null ? null : k.status().name());
        o.put("statusLabel", k.statusLabel());
        o.put("overridden", k.override() != null);
        o.put("overrideReason", k.reason());
        o.put("required", k.required());
        return o;
    }

    private ObjectNode action(ActionView a) {
        ObjectNode o = json.createObjectNode();
        o.put("title", a.title());
        o.put("owner", a.owner());
        o.put("team", team(a.teamTag()));
        o.put("due", a.dueDate() == null ? null : a.dueDate().toString());
        o.put("status", a.status());
        o.put("overdue", a.overdue());
        o.put("carriedForward", a.originMeetingId() != null);
        return o;
    }

    private ArrayNode comments(List<Comment> list) {
        ArrayNode out = json.createArrayNode();
        for (Comment c : list) {
            if (c.getParentId() != null) continue;
            ObjectNode o = out.addObject();
            o.put("author", c.getAuthor());
            o.put("body", c.getBody());
            o.put("resolved", c.isResolved());
            o.put("createdAt", c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
            ArrayNode replies = o.putArray("replies");
            list.stream().filter(x -> c.getId().equals(x.getParentId())).forEach(x -> replies.addObject()
                    .put("author", x.getAuthor()).put("body", x.getBody()).put("createdAt", x.getCreatedAt() == null ? null : x.getCreatedAt().toString()));
        }
        return out;
    }

    /** Theme name, colours and fonts, so a converter can style the Word document like the deck. */
    private ObjectNode theme(long meetingId) {
        ObjectNode t = json.createObjectNode();
        try {
            MeetingThemes.Choice choice = themes.choice(meetingId, false);
            t.put("name", choice.themeName());
            t.put("version", choice.versionNo());
        } catch (RuntimeException e) {
            t.putNull("name");
        }
        JsonNode s = themes.settings(meetingId);
        if (s != null && s.path("colors").isObject()) {
            t.set("colors", s.get("colors").deepCopy());
        }
        if (s != null && s.path("fonts").isObject()) {
            t.set("fonts", s.get("fonts").deepCopy());
        }
        return t;
    }

    private static String team(TeamTag t) {
        return t == TeamTag.ENGINEERING ? "Engineering" : t == TeamTag.OPERATIONS ? "Operations" : "Shared";
    }

    private JsonNode read(String s) {
        try {
            return s == null ? json.createObjectNode() : json.readTree(s);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }
}
