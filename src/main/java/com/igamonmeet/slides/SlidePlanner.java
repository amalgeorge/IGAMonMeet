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

    public Plan plan(Input in) {
        List<Draft> main = new ArrayList<>();
        List<Draft> appendix = new ArrayList<>();
        main.add(new Draft("IAM_TITLE", null, data("title", in.title(), "subtitle", in.monthLabel(), "meta", in.dateLabel()), false));

        List<SectionInput> shown = in.sections().stream().filter(s -> s.filled() || s.required()).toList();
        TeamTag lastTeam = null;
        for (int i = 0; i < shown.size(); i++) {
            SectionInput s = shown.get(i);
            if (s.team() != TeamTag.SHARED && s.team() != lastTeam) {
                List<String> names = new ArrayList<>();
                for (int j = i; j < shown.size() && shown.get(j).team() == s.team(); j++) {
                    names.add(shown.get(j).name());
                }
                main.add(new Draft("IAM_SECTION", null, data("title", teamName(s.team()), "body", String.join(", ", names)), false));
            }
            if (s.team() != TeamTag.SHARED) {
                lastTeam = s.team();
            }
            section(s, in, main, appendix);
        }
        long open = in.actions().stream().filter(a -> !a.closed()).count();
        main.add(new Draft("IAM_CLOSING", null, data("title", "Thank you",
                "body", open == 0 ? "No open action items." : open + " open action item" + (open == 1 ? "" : "s") + " carried to next month."), false));

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
            slides.add(new Slide(i + 1, d.layout(), d.sectionKey(), part, continuation, d.appendix(), d.data()));
        }
        return new Plan(slides, perSection, measure.family());
    }

    private void section(SectionInput s, Input in, List<Draft> main, List<Draft> appendix) {
        String team = teamLabel(s.team());
        if (!s.filled()) {
            main.add(new Draft(s.layout(), s.key(), data("team", team, "title", s.name(), "empty", "Not provided yet."), false));
            return;
        }
        switch (s.kind()) {
            case SUMMARY -> summary(s, in, team, main, appendix);
            case TWO_COLUMN -> twoColumn(s, in, team, main, appendix);
            case KPI -> kpis(s, team, main, appendix);
            case CHART -> chart(s, team, main);
            case TABLE -> table(s, in, team, s.columns(), rows(s.content()), null, main, appendix);
            case ACTION_ITEMS -> {
                List<List<String>> rows = new ArrayList<>();
                List<String> flags = new ArrayList<>();
                for (ActionRow a : in.actions()) {
                    rows.add(List.of(a.title(), a.owner(), a.team(), a.due()));
                    flags.add(a.closed() ? "closed" : a.overdue() ? "overdue" : "");
                }
                table(s, in, team, List.of("Action", "Owner", "Team", "Due"), rows, flags, main, appendix);
            }
            default -> main.add(new Draft(s.layout(), s.key(), data("team", team, "title", s.name()), false));
        }
    }

    // ---- lists (summary, two-column) ----

    private void summary(SectionInput s, Input in, String team, List<Draft> main, List<Draft> appendix) {
        List<String> points = texts(s.content().path("points"));
        Box box = in.boxes().getOrDefault("IAM_SUMMARY", new Box(in.slideWidth() * 0.5475, in.slideHeight() * 0.6133));
        Fitted f = fitList(points, SUMMARY_PT, box, 24, 14, s.rule());
        List<Object> stats = new ArrayList<>();
        s.content().path("stats").forEach(st -> stats.add(data("value", st.path("value").asText(), "label", st.path("label").asText())));
        emit(s, team, f, (page, first) -> data("points", page, "fontSize", f.size(), "stats", first ? stats : List.of()), main, appendix);
    }

    private void twoColumn(SectionInput s, Input in, String team, List<Draft> main, List<Draft> appendix) {
        JsonNode c = s.content();
        List<String> left = texts(c.path("left"));
        List<String> right = texts(c.path("right"));
        Box box = in.boxes().getOrDefault("IAM_TWO_COLUMN", new Box(in.slideWidth() * 0.405, in.slideHeight() * 0.48));
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
            last = new Fitted(size, paginate(items, t -> measure.height(t, size, false, box.w() - indent) + gap, box.h()));
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
        List<List<String>> pages = f.pages().isEmpty() ? List.of(List.of()) : f.pages();
        for (int p = 0; p < pages.size(); p++) {
            boolean toAppendix = s.rule() == OverflowRule.APPENDIX && p > 0;
            Map<String, Object> d = pd.make(pages.get(p), p == 0);
            d.put("team", team);
            d.put("title", toAppendix ? "Appendix: " + s.name() : s.name());
            if (s.rule() == OverflowRule.APPENDIX && p == 0 && pages.size() > 1) {
                d.put("note", "Continued in the appendix.");
            }
            (toAppendix ? appendix : main).add(new Draft(s.layout(), s.key(), d, toAppendix));
        }
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

    private void kpis(SectionInput s, String team, List<Draft> main, List<Draft> appendix) {
        List<KpiView> all = s.kpis();
        String commentary = s.content().path("commentary").asText("");
        int pages = Math.max(1, (all.size() + KPIS_PER_SLIDE - 1) / KPIS_PER_SLIDE);
        for (int p = 0; p < pages; p++) {
            boolean toAppendix = s.rule() == OverflowRule.APPENDIX && p > 0;
            List<KpiView> chunk = all.subList(p * KPIS_PER_SLIDE, Math.min(all.size(), (p + 1) * KPIS_PER_SLIDE));
            Map<String, Object> d = data("team", team, "title", toAppendix ? "Appendix: " + s.name() : s.name(),
                    "kpis", chunk, "body", p == 0 ? commentary : "");
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
                "commentary", texts(s.content().path("commentary"))), false));
    }

    // ---- tables ----

    private void table(SectionInput s, Input in, String team, List<String> head, List<List<String>> rows, List<String> flags,
                       List<Draft> main, List<Draft> appendix) {
        double width = in.slideWidth() * 0.91;
        int n = Math.max(head.size(), 1);
        double[] cols = new double[n];
        for (int i = 0; i < n; i++) {
            cols[i] = n == 1 ? width : i == 0 ? width * 0.45 : width * 0.55 / (n - 1);
        }
        double headH = TABLE_HEAD_PT * 1.2 + CELL_PAD_Y;
        double cap = in.slideHeight() * (CONTENT_BOTTOM - CONTENT_TOP) - headH;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            idx.add(i);
        }
        ToDoubleFunction<Integer> rowH = i -> {
            double h = 0;
            List<String> r = rows.get(i);
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
            if (s.rule() == OverflowRule.APPENDIX && p == 0 && pages.size() > 1) {
                int more = rows.size() - pages.get(0).size();
                d.put("note", "+" + more + " more in the appendix");
            }
            (toAppendix ? appendix : main).add(new Draft(s.layout(), s.key(), d, toAppendix));
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
