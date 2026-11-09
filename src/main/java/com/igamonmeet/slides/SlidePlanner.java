package com.igamonmeet.slides;

import com.fasterxml.jackson.databind.JsonNode;
import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.slides.PlanModel.ActionRow;
import com.igamonmeet.slides.PlanModel.Box;
import com.igamonmeet.slides.PlanModel.Input;
import com.igamonmeet.slides.PlanModel.Plan;
import com.igamonmeet.slides.PlanModel.SectionInput;
import com.igamonmeet.slides.PlanModel.Slide;
import com.igamonmeet.template.OverflowRule;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/**
 * Turns a Meeting into its Slide plan (ADR 0004). Pure: same input, same slides.
 * Overflow rules: CONTINUE splits onto continuation slides; SHRINK_TO_FIT steps text down (never below 14 pt) and then
 * continues; APPENDIX keeps what fits and moves the rest to appendix slides at the end. Charts never split.
 */
@Component
public class SlidePlanner {

    static final float TABLE_PT = 14;
    static final float TABLE_HEAD_PT = 12;
    static final float SUMMARY_PT = 18;
    static final float TWO_COLUMN_PT = 16;
    static final float MIN_PT = 14;
    static final int KPIS_PER_SLIDE = 4;
    static final int CHART_POINTS = 6;
    /** Content area for title-only layouts: from below the title to above the footer (% of slide height). */
    static final double CONTENT_TOP = 0.267;
    static final double CONTENT_BOTTOM = 0.88;
    static final double CELL_PAD_X = 21.6;
    static final double CELL_PAD_Y = 11.5;
    static final double NOTE_H = 24;

    private final TextMeasurer measure;

    public SlidePlanner(TextMeasurer measure) {
        this.measure = measure;
    }

    private record Draft(String layout, String sectionKey, Map<String, Object> data, boolean appendix) {
    }

    /** KPI descriptions go into the speaker notes of the slide that shows the KPI (docs/spec-v2.md §2.3). */
    private static void kpiDescriptions(Map<String, Object> data) {
        if (!(data.get("kpis") instanceof List<?> kpis)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : kpis) {
            if (o instanceof com.igamonmeet.meeting.KpiView k && k.description() != null) {
                sb.append(sb.length() == 0 ? "" : "\n").append(k.name()).append(": ").append(k.description());
            }
        }
        if (sb.length() > 0) {
            Object existing = data.get("notes");
            data.put("notes", existing == null || existing.toString().isBlank() ? sb.toString() : existing + "\n\n" + sb);
        }
    }

    public Plan plan(Input in) {
        List<Draft> main = new ArrayList<>();
        List<Draft> appendix = new ArrayList<>();
        main.add(new Draft("IAM_TITLE", null, data("title", in.title(), "subtitle", in.monthLabel(), "meta", in.dateLabel()), false));

        List<SectionInput> shown = in.sections().stream().filter(s -> s.filled() || s.required()).toList();
        TeamTag lastTeam = null;
        String lastGroup = null;
        for (int i = 0; i < shown.size(); i++) {
            SectionInput s = shown.get(i);
            String groupKey = s.group() == null ? null : s.group().key();
            if (groupKey != null) {
                // Grouped Sections get their group's divider instead of a team divider (spec-v2 §1.5).
                if (!groupKey.equals(lastGroup) && s.group().divider()) {
                    List<String> names = new ArrayList<>();
                    for (int j = i; j < shown.size() && shown.get(j).group() != null && groupKey.equals(shown.get(j).group().key()); j++) {
                        names.add(shown.get(j).name());
                    }
                    Map<String, Object> div = data("title", s.group().name(), "body", String.join(", ", names), "dividerKey", "group:" + groupKey);
                    div.put("linkKeys", sectionKeysFrom(shown, i, j -> shown.get(j).group() != null && groupKey.equals(shown.get(j).group().key())));
                    main.add(new Draft("IAM_SECTION", null, div, false));
                }
                lastGroup = groupKey;
                lastTeam = null;
            } else if (s.team() != TeamTag.SHARED && s.team() != lastTeam) {
                List<String> names = new ArrayList<>();
                for (int j = i; j < shown.size() && shown.get(j).team() == s.team() && shown.get(j).group() == null; j++) {
                    names.add(shown.get(j).name());
                }
                Map<String, Object> div = data("title", teamName(s.team()), "body", String.join(", ", names));
                TeamTag team = s.team();
                div.put("linkKeys", sectionKeysFrom(shown, i, j -> shown.get(j).team() == team && shown.get(j).group() == null));
                main.add(new Draft("IAM_SECTION", null, div, false));
            }
            if (groupKey == null) {
                lastGroup = null;
                if (s.team() != TeamTag.SHARED) {
                    lastTeam = s.team();
                }
            }
            int first = main.size();
            section(s, in, main, appendix);
            String notes = s.content() == null ? "" : s.content().path("notes").asText("");
            if (!notes.isBlank()) {
                // Notes go on the Section's first slide only; continuation and appendix slides stay clean.
                (first < main.size() ? main.get(first) : appendix.get(appendix.size() - 1)).data().put("notes", notes);
            }
        }
        long open = in.actions().stream().filter(a -> !a.closed()).count();
        main.add(new Draft("IAM_CLOSING", null, data("title", "Thank you",
                "body", open == 0 ? "No open action items." : open + " open action item" + (open == 1 ? "" : "s") + " carried to next month."), false));

        if (in.agenda()) {
            main.addAll(1, agenda(shown));
        }
        List<Draft> all = new ArrayList<>(main);
        all.addAll(appendix);
        Map<String, Integer> perSection = new LinkedHashMap<>();
        all.stream().filter(d -> d.sectionKey() != null).forEach(d -> perSection.merge(d.sectionKey(), 1, Integer::sum));

        Map<String, Integer> seen = new LinkedHashMap<>();
        List<Slide> slides = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Draft d = all.get(i);
            String part = null;
            boolean continuation = false;
            if (d.sectionKey() != null) {
                int total = perSection.get(d.sectionKey());
                int k = seen.merge(d.sectionKey(), 1, Integer::sum);
                continuation = k > 1;
                if (total > 1) {
                    part = k + " of " + total;
                    d.data().put("title", d.data().get("title") + " (" + part + ")");
                }
            }
            kpiDescriptions(d.data());
            slides.add(new Slide(i + 1, d.layout(), d.sectionKey(), part, continuation, d.appendix(), d.data()));
        }
        if (in.agenda()) {
            link(slides);
        }
        return new Plan(slides, perSection, measure.family());
    }

    // ---- agenda and links (spec-v3 §5) ----

    static final int AGENDA_PER_SLIDE = 12;

    private interface Member {
        boolean test(int j);
    }

    /** Keys of the run of Sections starting at i for which {@code member} holds. */
    private static List<String> sectionKeysFrom(List<SectionInput> shown, int i, Member member) {
        List<String> keys = new ArrayList<>();
        for (int j = i; j < shown.size() && member.test(j); j++) keys.add(shown.get(j).key());
        return keys;
    }

    /** Agenda entries in Section order: a group heading (level 0) before its Sections (level 1); ungrouped Sections at level 0. */
    private List<Draft> agenda(List<SectionInput> shown) {
        List<Map<String, Object>> entries = new ArrayList<>();
        String lastGroup = null;
        for (SectionInput s : shown) {
            String g = s.group() == null ? null : s.group().key();
            if (g != null && !g.equals(lastGroup)) {
                entries.add(data("text", s.group().name(), "level", 0, "key", s.group().divider() ? "group:" + g : s.key()));
            }
            lastGroup = g;
            entries.add(data("text", s.name(), "level", g == null ? 0 : 1, "key", s.key()));
        }
        List<Draft> out = new ArrayList<>();
        int pages = Math.max(1, (entries.size() + AGENDA_PER_SLIDE - 1) / AGENDA_PER_SLIDE);
        for (int p = 0; p < pages; p++) {
            List<Map<String, Object>> page = entries.subList(p * AGENDA_PER_SLIDE, Math.min(entries.size(), (p + 1) * AGENDA_PER_SLIDE));
            out.add(new Draft("IAM_SUMMARY", null, data("title", pages > 1 ? "Agenda (" + (p + 1) + " of " + pages + ")" : "Agenda",
                    "agenda", new ArrayList<>(page), "isAgenda", true), false));
        }
        return out;
    }

    /** Resolves link keys to slide numbers: agenda entries, divider lists, and "↩ Agenda" on every content slide. */
    private static void link(List<Slide> slides) {
        Map<String, Integer> first = new LinkedHashMap<>();
        Integer agendaN = null;
        for (Slide s : slides) {
            if (s.sectionKey() != null && !s.appendix()) first.putIfAbsent(s.sectionKey(), s.n());
            if (s.data().get("dividerKey") instanceof String k) first.putIfAbsent(k, s.n());
            if (agendaN == null && Boolean.TRUE.equals(s.data().get("isAgenda"))) agendaN = s.n();
        }
        for (Slide s : slides) {
            if (s.data().get("agenda") instanceof List<?> entries) {
                for (Object o : entries) {
                    @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) o;
                    Integer t = first.get(String.valueOf(e.get("key")));
                    if (t != null) e.put("target", t);
                }
            }
            if (s.data().get("linkKeys") instanceof List<?> keys) {
                List<Map<String, Object>> links = new ArrayList<>();
                for (Object k : keys) {
                    Integer t = first.get(String.valueOf(k));
                    String name = slides.stream().filter(x -> String.valueOf(k).equals(x.sectionKey())).findFirst()
                            .map(x -> String.valueOf(x.data().get("title")).replaceAll(" \\(\\d+ of \\d+\\)$", "")).orElse(String.valueOf(k));
                    if (t != null) links.add(data("text", name, "target", t));
                }
                s.data().put("links", links);
            }
            if (agendaN != null && s.sectionKey() != null) s.data().put("agendaTarget", agendaN);
        }
    }

    private void section(SectionInput s, Input in, List<Draft> main, List<Draft> appendix) {
        int mainStart = main.size();
        int appendixStart = appendix.size();
        sectionSlides(s, in, main, appendix);
        // Admin's slide layout (spec-v3 §6): the Section's own slides carry the boxes; renderers place shapes in them.
        SlideLayout.Frame fr = SlideLayout.frame(s.slideLayout());
        if (fr != null) {
            Map<String, Object> frameData = fr.data();
            for (List<Draft> list : List.of(main.subList(mainStart, main.size()), appendix.subList(appendixStart, appendix.size()))) {
                for (Draft d : list) {
                    if (d.layout().equals(s.layout()) && !d.data().containsKey("empty")) d.data().put("frame", frameData);
                }
            }
        }
    }

    /** The Section's box for this layout, or null: only the Section's own layout follows the Admin's slide layout. */
    private static SlideLayout.Rect box(SectionInput s, String layout, java.util.function.Function<SlideLayout.Frame, SlideLayout.Rect> pick) {
        SlideLayout.Frame fr = SlideLayout.frame(s.slideLayout());
        return fr == null || !layout.equals(s.layout()) ? null : pick.apply(fr);
    }

    private void sectionSlides(SectionInput s, Input in, List<Draft> main, List<Draft> appendix) {
        String team = teamLabel(s.team());
        if (!s.filled()) {
            main.add(new Draft(s.layout(), s.key(), data("team", team, "title", s.name(), "empty", "Not provided yet."), false));
            return;
        }
        switch (s.kind()) {
            case SUMMARY -> summary(s, in, team, main, appendix);
            case TEXT -> text(s, in, team, main, appendix);
            case TWO_COLUMN -> twoColumn(s, in, team, main, appendix);
            case KPI -> kpis(s, in, team, main, appendix);
            case CHART -> chart(s, team, main);
            case TABLE -> table(s, in, team, s.columns(), rows(s.content()), null, main, appendix, s.layout(), TableStyle.of(s.tableStyle()));
            case ACTION_ITEMS -> {
                List<List<String>> rows = new ArrayList<>();
                List<String> flags = new ArrayList<>();
                for (ActionRow a : in.actions()) {
                    rows.add(List.of(a.title(), a.owner(), a.team(), a.due()));
                    flags.add(a.closed() ? "closed" : a.overdue() ? "overdue" : "");
                }
                table(s, in, team, List.of("Action", "Owner", "Team", "Due"), rows, flags, main, appendix, s.layout(), TableStyle.NONE);
            }
            default -> main.add(new Draft(s.layout(), s.key(), data("team", team, "title", s.name()), false));
        }
        // Formatted text (spec-v3 §2): Text Sections flow it with their blocks; other kinds continue on text slides.
        if (s.kind() != SectionKind.TEXT && !RichText.isEmpty(s.content().path("rich"))) {
            textRun(s, in, team, RichText.lines(s.content().path("rich")), main, appendix, "IAM_SUMMARY");
        }
    }

    // ---- lists (summary, two-column) ----

    private void summary(SectionInput s, Input in, String team, List<Draft> main, List<Draft> appendix) {
        List<String> points = texts(s.content().path("points"));
        SlideLayout.Rect area = box(s, s.layout(), SlideLayout.Frame::content);
        Box box = area != null ? new Box(in.slideWidth() * area.w(), in.slideHeight() * area.h())
                : in.boxes().getOrDefault("IAM_SUMMARY", new Box(in.slideWidth() * 0.5475, in.slideHeight() * 0.6133));
        Fitted f = fitList(points, SUMMARY_PT, box, 24, 14, s.rule());
        List<Object> stats = new ArrayList<>();
        s.content().path("stats").forEach(st -> stats.add(data("value", st.path("value").asText(), "label", st.path("label").asText())));
        emit(s, team, f, (page, first) -> data("points", page, "fontSize", f.size(), "stats", first ? stats : List.of()), main, appendix);
    }

    private static final char BULLET = '\u0001';

    /** The blocks of a Text Section as lines; a bullet item is marked so the slide can show it as a list item. */
    public static List<String> textLines(JsonNode content) {
        List<String> lines = new ArrayList<>();
        if (content.path("text").isTextual() && !content.has("blocks")) {
            lines.add(content.get("text").asText()); // plain text from before rich content: one paragraph
        }
        for (JsonNode b : content.path("blocks")) {
            switch (b.path("type").asText()) {
                case "paragraph" -> {
                    if (!b.path("text").asText("").isBlank()) lines.add(b.path("text").asText());
                }
                case "bullets" -> b.path("items").forEach(i -> {
                    if (!i.asText("").isBlank()) lines.add(BULLET + i.asText());
                });
                default -> { }
            }
        }
        lines.addAll(RichText.lines(content.path("rich")));
        return lines;
    }

    private void text(SectionInput s, Input in, String team, List<Draft> main, List<Draft> appendix) {
        JsonNode blocks = s.content().path("blocks");
        boolean hasTable = false;
        for (JsonNode b : blocks) hasTable |= "table".equals(b.path("type").asText());
        if (!hasTable) {
            textRun(s, in, team, textLines(s.content()), main, appendix, s.layout());
            return;
        }
        // Text runs go on IAM_SUMMARY slides; each table block gets IAM_TABLE slides of its own, in block order (spec-v2 §3.2).
        com.fasterxml.jackson.databind.node.ArrayNode run = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (JsonNode b : blocks) {
            if (!"table".equals(b.path("type").asText())) {
                run.add(b);
                continue;
            }
            textRun(s, in, team, linesOf(run), main, appendix, s.layout());
            run.removeAll();
            List<String> head = cells(b.path("columns"));
            List<List<String>> rows = new ArrayList<>();
            b.path("rows").forEach(r -> rows.add(cells(r)));
            if (!rows.isEmpty()) {
                table(s, in, team, head, rows, null, main, appendix, "IAM_TABLE", TableStyle.of(b.path("style")));
            }
        }
        List<String> tail = new ArrayList<>(linesOf(run));
        tail.addAll(RichText.lines(s.content().path("rich")));
        textRun(s, in, team, tail, main, appendix, s.layout());
    }

    private static List<String> linesOf(com.fasterxml.jackson.databind.node.ArrayNode blocks) {
        com.fasterxml.jackson.databind.node.ObjectNode c = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        c.set("blocks", blocks.deepCopy());
        return textLines(c); // no "rich" here: the caller adds it after the last run
    }

    /** Every cell as text, blanks kept so columns stay aligned. */
    private static List<String> cells(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText("")));
        return out;
    }

    private void textRun(SectionInput s, Input in, String team, List<String> lines, List<Draft> main, List<Draft> appendix, String layout) {
        if (lines.isEmpty()) {
            return;
        }
        // Text Sections use the whole content width of the IAM_SUMMARY layout (no stat tiles), or the Admin's content box.
        SlideLayout.Rect area = box(s, layout, SlideLayout.Frame::content);
        Box box = area != null ? new Box(in.slideWidth() * area.w(), in.slideHeight() * area.h())
                : new Box(in.slideWidth() * 0.91, in.boxes().getOrDefault("IAM_SUMMARY", new Box(0, in.slideHeight() * 0.6133)).h());
        Fitted f = fitList(lines, SUMMARY_PT, box, 24, 12, s.rule());
        emit(s, team, f, (page, first) -> {
            List<Map<String, Object>> blocks = new ArrayList<>();
            for (String l : page) blocks.add(lineBlock(l));
            return data("blocks", blocks, "fontSize", f.size(), "wide", true);
        }, main, appendix, layout);
    }

    private void twoColumn(SectionInput s, Input in, String team, List<Draft> main, List<Draft> appendix) {
        JsonNode c = s.content();
        List<String> left = texts(c.path("left"));
        List<String> right = texts(c.path("right"));
        SlideLayout.Rect area = box(s, s.layout(), SlideLayout.Frame::left);
        Box box = area != null ? new Box(in.slideWidth() * area.w(), in.slideHeight() * area.h() * 0.85) // heading takes the top
                : in.boxes().getOrDefault("IAM_TWO_COLUMN", new Box(in.slideWidth() * 0.405, in.slideHeight() * 0.48));
        float size = TWO_COLUMN_PT;
        List<List<String>> lp = null;
        List<List<String>> rp = null;
        for (float candidate : sizes(TWO_COLUMN_PT, s.rule())) {
            size = candidate;
            lp = paginate(left, t -> measure.height(t, candidate, false, box.w() - 20) + 10, box.h());
            rp = paginate(right, t -> measure.height(t, candidate, false, box.w() - 20) + 10, box.h());
            if (lp.size() <= 1 && rp.size() <= 1) {
                break;
            }
        }
        int pages = Math.max(Math.max(lp.size(), rp.size()), 1);
        String leftHead = c.path("leftHead").asText("Risks");
        String rightHead = c.path("rightHead").asText("Decisions needed");
        for (int p = 0; p < pages; p++) {
            boolean toAppendix = s.rule() == OverflowRule.APPENDIX && p > 0;
            Map<String, Object> d = data("team", team, "title", toAppendix ? "Appendix: " + s.name() : s.name(),
                    "leftHead", leftHead, "left", p < lp.size() ? lp.get(p) : List.of(),
                    "rightHead", rightHead, "right", p < rp.size() ? rp.get(p) : List.of(), "fontSize", size);
            if (s.rule() == OverflowRule.APPENDIX && p == 0 && pages > 1) {
                d.put("note", "Continued in the appendix.");
            }
            (toAppendix ? appendix : main).add(new Draft(s.layout(), s.key(), d, toAppendix));
        }
    }

    private record Fitted(float size, List<List<String>> pages) {
    }

    private Fitted fitList(List<String> items, float base, Box box, double indent, double gap, OverflowRule rule) {
        Fitted last = null;
        for (float size : sizes(base, rule)) {
            last = new Fitted(size, paginate(items, t -> measure.height(RichText.strip(t), size, false, box.w() - indent) + gap, box.h()));
            if (last.pages().size() <= 1) {
                return last;
            }
        }
        return last;
    }

    private interface PageData {
        Map<String, Object> make(List<String> page, boolean first);
    }

    private void emit(SectionInput s, String team, Fitted f, PageData pd, List<Draft> main, List<Draft> appendix) {
        emit(s, team, f, pd, main, appendix, s.layout());
    }

    private void emit(SectionInput s, String team, Fitted f, PageData pd, List<Draft> main, List<Draft> appendix, String layout) {
        List<List<String>> pages = f.pages().isEmpty() ? List.of(List.of()) : f.pages();
        for (int p = 0; p < pages.size(); p++) {
            boolean toAppendix = s.rule() == OverflowRule.APPENDIX && p > 0;
            Map<String, Object> d = pd.make(pages.get(p), p == 0);
            d.put("team", team);
            d.put("title", toAppendix ? "Appendix: " + s.name() : s.name());
            if (s.rule() == OverflowRule.APPENDIX && p == 0 && pages.size() > 1) {
                d.put("note", "Continued in the appendix.");
            }
            (toAppendix ? appendix : main).add(new Draft(layout, s.key(), d, toAppendix));
        }
    }

    /**
     * One text line as a slide block: "p", "li" (bullet) or "oli" (numbered, with "num"), "level" 0 or 1 for sub-items.
     * "text" keeps the inline marks of {@link RichText}; renderers turn them into bold, italic and links.
     */
    static Map<String, Object> lineBlock(String l) {
        String type = "p";
        String num = null;
        int i = 0;
        if (!l.isEmpty() && l.charAt(0) == RichText.BULLET) {
            type = "li";
            i = 1;
        } else if (!l.isEmpty() && l.charAt(0) == RichText.NUMBER) {
            int end = l.indexOf(RichText.NUMBER_END);
            type = "oli";
            num = l.substring(1, end);
            i = end + 1;
        }
        int level = 0;
        if (i < l.length() && l.charAt(i) == RichText.SUB) {
            level = 1;
            i++;
        }
        Map<String, Object> b = data("type", type, "text", l.substring(i));
        if (level > 0) b.put("level", level);
        if (num != null) b.put("num", num);
        return b;
    }

    /** Text sizes to try: the base size, then (only for SHRINK_TO_FIT) smaller steps down to 14 pt. */
    private static List<Float> sizes(float base, OverflowRule rule) {
        List<Float> out = new ArrayList<>(List.of(base));
        if (rule == OverflowRule.SHRINK_TO_FIT) {
            for (float s = base - 2; s >= MIN_PT; s -= 2) {
                out.add(s);
            }
        }
        return out;
    }

    // ---- KPIs and charts ----

    /** Tiles per slide from the Section's KPI layout (spec-v2 §2.5): 2, 3, 4 (default), 6 or 8, or a table. */
    static int tilesPerSlide(String kpiLayout) {
        return switch (kpiLayout == null ? "4" : kpiLayout) {
            case "2" -> 2;
            case "3" -> 3;
            case "6" -> 6;
            case "8" -> 8;
            default -> KPIS_PER_SLIDE;
        };
    }

    private void kpis(SectionInput s, Input in, String team, List<Draft> main, List<Draft> appendix) {
        List<KpiView> all = s.kpis();
        // Formatted text replaces the old commentary box (spec-v3 §2); it continues on its own text slides.
        String commentary = RichText.isEmpty(s.content().path("rich")) ? s.content().path("commentary").asText("") : "";
        if ("TABLE".equals(s.kpiLayout())) {
            List<List<String>> rows = new ArrayList<>();
            for (KpiView k : all) {
                rows.add(List.of(k.name(), k.display() == null ? "—" : k.display(), k.targetLine() == null ? "" : k.targetLine(),
                        k.statusLabel() == null ? "" : k.statusLabel()));
            }
            int first = main.size();
            table(s, in, team, List.of("KPI", "This month", "Target", "Status"), rows, null, main, appendix, "IAM_TABLE", TableStyle.NONE);
            if (first < main.size()) {
                main.get(first).data().put("kpis", all); // descriptions reach the speaker notes; table slides ignore the tiles
                if (!commentary.isBlank()) main.get(first).data().put("note", commentary);
            }
            return;
        }
        SlideLayout.Frame fr = SlideLayout.frame(s.slideLayout());
        int per = fr != null && !fr.tiles().isEmpty() ? fr.tiles().size() : tilesPerSlide(s.kpiLayout());
        int pages = Math.max(1, (all.size() + per - 1) / per);
        for (int p = 0; p < pages; p++) {
            boolean toAppendix = s.rule() == OverflowRule.APPENDIX && p > 0;
            List<KpiView> chunk = all.subList(p * per, Math.min(all.size(), (p + 1) * per));
            Map<String, Object> d = data("team", team, "title", toAppendix ? "Appendix: " + s.name() : s.name(),
                    "kpis", chunk, "body", p == 0 ? commentary : "", "perSlide", per);
            (toAppendix ? appendix : main).add(new Draft(s.layout(), s.key(), d, toAppendix));
        }
    }

    private void chart(SectionInput s, String team, List<Draft> main) {
        PlanModel.Series series = s.chart();
        List<String> labels = series == null ? List.of() : series.labels();
        List<?> values = series == null ? List.of() : series.values();
        int from = Math.max(0, labels.size() - CHART_POINTS);
        main.add(new Draft(s.layout(), s.key(), data("team", team, "title", s.name(),
                "labels", labels.subList(from, labels.size()), "values", values.subList(from, values.size()),
                "target", series == null ? null : series.target(), "unit", series == null ? "" : series.unit(),
                "commentary", RichText.isEmpty(s.content().path("rich")) ? texts(s.content().path("commentary")) : List.of()), false));
    }

    // ---- tables ----

    private void table(SectionInput s, Input in, String team, List<String> head, List<List<String>> rows, List<String> flags,
                       List<Draft> main, List<Draft> appendix, String layout, TableStyle style) {
        SlideLayout.Rect area = box(s, layout, SlideLayout.Frame::content);
        double width = area != null ? in.slideWidth() * area.w() : in.slideWidth() * 0.91;
        int n = Math.max(head.size(), 1);
        double[] cols = new double[n];
        boolean sized = style.widths() != null && style.widths().size() == n;
        double share = sized ? style.widths().stream().mapToDouble(Double::doubleValue).sum() : 0;
        for (int i = 0; i < n; i++) {
            cols[i] = sized ? width * style.widths().get(i) / share
                    : n == 1 ? width : i == 0 ? width * 0.45 : width * 0.55 / (n - 1);
        }
        // Status colours and the total row are row flags, like "overdue" on Action items (spec-v2 §3.3).
        if (style.statusColumn() != null || style.total()) {
            List<String> styled = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                String f = flags == null ? "" : flags.get(i);
                String st = style.statusColumn() == null || style.statusColumn() >= rows.get(i).size() ? null
                        : TableStyle.statusOf(rows.get(i).get(style.statusColumn()));
                styled.add(f.isEmpty() && st != null ? "status:" + st : f);
            }
            List<List<String>> all = new ArrayList<>(rows);
            if (style.total() && !rows.isEmpty()) {
                all.add(TableStyle.totalRow(head, rows));
                styled.add("total");
            }
            rows = all;
            flags = styled;
        }
        final List<List<String>> tableRows = rows;
        double headH = TABLE_HEAD_PT * 1.2 + CELL_PAD_Y;
        double cap = (area != null ? in.slideHeight() * area.h() : in.slideHeight() * (CONTENT_BOTTOM - CONTENT_TOP)) - headH;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            idx.add(i);
        }
        ToDoubleFunction<Integer> rowH = i -> {
            double h = 0;
            List<String> r = tableRows.get(i);
            for (int c = 0; c < n && c < r.size(); c++) {
                h = Math.max(h, measure.height(r.get(c), TABLE_PT, false, cols[c] - CELL_PAD_X));
            }
            return Math.max(h, TABLE_PT * 1.2) + CELL_PAD_Y;
        };

        List<List<Integer>> pages;
        if (s.rule() == OverflowRule.APPENDIX) {
            List<List<Integer>> firstFit = paginate(idx, rowH, cap - NOTE_H);
            pages = new ArrayList<>();
            pages.add(firstFit.isEmpty() ? List.of() : firstFit.get(0));
            List<Integer> rest = idx.subList(pages.get(0).size(), idx.size());
            pages.addAll(paginate(rest, rowH, cap));
        } else {
            pages = paginate(idx, rowH, cap);
        }
        if (pages.isEmpty()) {
            pages = List.of(List.of());
        }
        for (int p = 0; p < pages.size(); p++) {
            boolean toAppendix = s.rule() == OverflowRule.APPENDIX && p > 0;
            List<List<String>> pageRows = new ArrayList<>();
            List<String> pageFlags = new ArrayList<>();
            for (int i : pages.get(p)) {
                pageRows.add(rows.get(i));
                pageFlags.add(flags == null ? "" : flags.get(i));
            }
            Map<String, Object> d = data("team", team, "title", toAppendix ? "Appendix: " + s.name() : s.name(),
                    "head", head, "rows", pageRows, "rowFlags", pageFlags, "colWidths", widthsPct(cols, width), "fontSize", TABLE_PT);
            if (style.align() != null) d.put("align", style.align());
            if (style.banded()) d.put("banded", true);
            if (style.alertColumn() != null) d.put("alertColumn", style.alertColumn()); // cells above 0 shown red (spec-v3 §1.3)
            if (s.rule() == OverflowRule.APPENDIX && p == 0 && pages.size() > 1) {
                int more = rows.size() - pages.get(0).size();
                d.put("note", "+" + more + " more in the appendix");
            }
            (toAppendix ? appendix : main).add(new Draft(layout, s.key(), d, toAppendix));
        }
    }

    private static List<Double> widthsPct(double[] cols, double width) {
        List<Double> out = new ArrayList<>();
        for (double c : cols) {
            out.add(Math.round(c / width * 1000) / 10.0);
        }
        return out;
    }

    // ---- helpers ----

    /** Greedy pages: fill each page up to {@code cap}; an item taller than a page still gets a page of its own. */
    static <T> List<List<T>> paginate(List<T> items, ToDoubleFunction<T> height, double cap) {
        List<List<T>> pages = new ArrayList<>();
        List<T> page = new ArrayList<>();
        double used = 0;
        for (T item : items) {
            double h = height.applyAsDouble(item);
            if (!page.isEmpty() && used + h > cap) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
            }
            page.add(item);
            used += h;
        }
        if (!page.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }

    private static List<List<String>> rows(JsonNode content) {
        List<List<String>> out = new ArrayList<>();
        content.path("rows").forEach(r -> {
            List<String> row = new ArrayList<>();
            r.forEach(c -> row.add(c.asText()));
            out.add(row);
        });
        return out;
    }

    private static List<String> texts(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> {
            if (!n.asText().isBlank()) {
                out.add(n.asText());
            }
        });
        return out;
    }

    private static String teamName(TeamTag t) {
        return t == TeamTag.ENGINEERING ? "IAM Engineering" : t == TeamTag.OPERATIONS ? "IAM Operations" : "Shared";
    }

    private static String teamLabel(TeamTag t) {
        return teamName(t).toUpperCase();
    }

    static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
