package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.slides.PlanModel.ActionRow;
import com.igamonmeet.slides.PlanModel.Input;
import com.igamonmeet.slides.PlanModel.Plan;
import com.igamonmeet.slides.PlanModel.SectionInput;
import com.igamonmeet.slides.PlanModel.Series;
import com.igamonmeet.slides.PlanModel.Slide;
import com.igamonmeet.slides.SlidePlanner;
import com.igamonmeet.slides.TextMeasurer;
import com.igamonmeet.template.Direction;
import com.igamonmeet.template.OverflowRule;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The planner is pure, so these run without Spring. */
class SlidePlannerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final SlidePlanner planner = new SlidePlanner(new TextMeasurer());

    private static Input input(List<SectionInput> sections, List<ActionRow> actions) {
        return new Input("IAM Monthly Meeting", "November 2026", "Thursday 26 November 2026", sections, actions, 960, 540, Map.of());
    }

    private static SectionInput table(String key, TeamTag team, OverflowRule rule, int rows) {
        ObjectNode c = JSON.createObjectNode();
        ArrayNode rs = c.putArray("rows");
        for (int i = 0; i < rows; i++) {
            rs.addArray().add("Initiative " + i).add("IAM Engineering").add("On track").add("Dec 2026");
        }
        return new SectionInput(key, "Roadmap", team, SectionKind.TABLE, "IAM_TABLE", rule, true, rows > 0,
                List.of("Initiative", "Owner", "Status", "Due"), c, List.of(), null);
    }

    private static SectionInput summary(OverflowRule rule, int points, String text) {
        ObjectNode c = JSON.createObjectNode();
        ArrayNode ps = c.putArray("points");
        for (int i = 0; i < points; i++) {
            ps.add(text);
        }
        c.putArray("stats");
        return new SectionInput("summary", "Executive summary", TeamTag.SHARED, SectionKind.SUMMARY, "IAM_SUMMARY", rule, true, true,
                List.of(), c, List.of(), null);
    }

    private static List<Slide> of(Plan p, String key) {
        return p.slides().stream().filter(s -> key.equals(s.sectionKey())).toList();
    }

    @Test
    void deckHasTitleDividersAndClosing() {
        Plan p = planner.plan(input(List.of(summary(OverflowRule.SHRINK_TO_FIT, 3, "Short point"),
                table("ops-table", TeamTag.OPERATIONS, OverflowRule.CONTINUE, 2),
                table("eng-table", TeamTag.ENGINEERING, OverflowRule.CONTINUE, 2)), List.of()));
        assertThat(p.slides()).extracting(Slide::layout)
                .containsExactly("IAM_TITLE", "IAM_SUMMARY", "IAM_SECTION", "IAM_TABLE", "IAM_SECTION", "IAM_TABLE", "IAM_CLOSING");
        assertThat(p.slides().get(2).data().get("title")).isEqualTo("IAM Operations");
    }

    @Test
    void longTablesContinueWithRepeatedHeader() {
        Plan p = planner.plan(input(List.of(table("roadmap", TeamTag.ENGINEERING, OverflowRule.CONTINUE, 40)), List.of()));
        List<Slide> pages = of(p, "roadmap");
        assertThat(pages.size()).isGreaterThan(1);
        assertThat(pages.get(0).data().get("title")).isEqualTo("Roadmap (1 of " + pages.size() + ")");
        assertThat(pages.get(1).continuation()).isTrue();
        assertThat(pages).allSatisfy(s -> assertThat(s.data().get("head")).isEqualTo(List.of("Initiative", "Owner", "Status", "Due")));
        int total = pages.stream().mapToInt(s -> ((List<?>) s.data().get("rows")).size()).sum();
        assertThat(total).isEqualTo(40);
        assertThat(of(planner.plan(input(List.of(table("roadmap", TeamTag.ENGINEERING, OverflowRule.CONTINUE, 3)), List.of())), "roadmap")).hasSize(1);
    }

    @Test
    void appendixRuleMovesOverflowToTheEnd() {
        Plan p = planner.plan(input(List.of(table("roadmap", TeamTag.ENGINEERING, OverflowRule.APPENDIX, 40)), List.of()));
        List<Slide> pages = of(p, "roadmap");
        assertThat(pages.get(0).appendix()).isFalse();
        assertThat((String) pages.get(0).data().get("note")).startsWith("+").endsWith("more in the appendix");
        int closing = p.slides().stream().filter(s -> s.layout().equals("IAM_CLOSING")).findFirst().orElseThrow().n();
        assertThat(pages.subList(1, pages.size())).allSatisfy(s -> {
            assertThat(s.appendix()).isTrue();
            assertThat(s.n()).isGreaterThan(closing);
        });
    }

    @Test
    void shrinkToFitStepsDownThenContinues() {
        String sentence = "Certification campaign behind plan; escalation needed for two business units and a revised timeline.";
        Plan fits = planner.plan(input(List.of(summary(OverflowRule.SHRINK_TO_FIT, 3, sentence)), List.of()));
        assertThat(of(fits, "summary")).hasSize(1);
        assertThat(of(fits, "summary").get(0).data().get("fontSize")).isEqualTo(18f);

        Plan crowded = planner.plan(input(List.of(summary(OverflowRule.SHRINK_TO_FIT, 40, sentence)), List.of()));
        List<Slide> pages = of(crowded, "summary");
        assertThat(pages.size()).isGreaterThan(1);
        assertThat(pages.get(0).data().get("fontSize")).isEqualTo(14f);

        Plan noShrink = planner.plan(input(List.of(summary(OverflowRule.CONTINUE, 40, sentence)), List.of()));
        assertThat(of(noShrink, "summary").get(0).data().get("fontSize")).isEqualTo(18f);
        assertThat(of(noShrink, "summary").size()).isGreaterThanOrEqualTo(pages.size());
    }

    @Test
    void kpisFourPerSlideAndChartsKeepRecentMonths() {
        List<KpiView> kpis = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            kpis.add(new KpiView("k" + i, "KPI " + i, "%", BigDecimal.TEN, Direction.HIGHER, BigDecimal.ONE, true,
                    BigDecimal.TEN, null, null, null, null, null));
        }
        SectionInput kpiSection = new SectionInput("kpis", "Key metrics", TeamTag.OPERATIONS, SectionKind.KPI, "IAM_KPI", OverflowRule.CONTINUE,
                true, true, List.of(), JSON.createObjectNode().put("commentary", "Note"), kpis, null);
        List<String> labels = List.of("Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov");
        List<BigDecimal> values = labels.stream().map(l -> BigDecimal.valueOf(90 + l.length())).toList();
        SectionInput chart = new SectionInput("trend", "SLA trend", TeamTag.OPERATIONS, SectionKind.CHART, "IAM_CHART", OverflowRule.CONTINUE,
                false, true, List.of(), JSON.createObjectNode().set("commentary", JSON.createArrayNode().add("Up")), List.of(),
                new Series(labels, values, BigDecimal.valueOf(95), "%"));
        Plan p = planner.plan(input(List.of(kpiSection, chart), List.of()));
        assertThat(of(p, "kpis")).hasSize(3);
        assertThat(of(p, "kpis").get(0).data().get("body")).isEqualTo("Note");
        assertThat(of(p, "kpis").get(1).data().get("body")).isEqualTo("");
        assertThat(of(p, "trend")).hasSize(1);
        assertThat(of(p, "trend").get(0).data().get("labels")).isEqualTo(List.of("Jun", "Jul", "Aug", "Sep", "Oct", "Nov"));
    }

    @Test
    void emptySectionsAndActionItems() {
        SectionInput optionalEmpty = new SectionInput("releases", "Releases", TeamTag.ENGINEERING, SectionKind.TABLE, "IAM_TABLE",
                OverflowRule.CONTINUE, false, false, List.of("A"), JSON.createObjectNode(), List.of(), null);
        SectionInput requiredEmpty = new SectionInput("incidents", "Incidents", TeamTag.OPERATIONS, SectionKind.TABLE, "IAM_TABLE",
                OverflowRule.CONTINUE, true, false, List.of("A"), JSON.createObjectNode(), List.of(), null);
        SectionInput actions = new SectionInput("actions", "Action items", TeamTag.SHARED, SectionKind.ACTION_ITEMS, "IAM_TABLE",
                OverflowRule.CONTINUE, true, true, List.of(), JSON.createObjectNode(), List.of(), null);
        Plan p = planner.plan(input(List.of(optionalEmpty, requiredEmpty, actions), List.of(
                new ActionRow("Close orphan accounts", "J. Tan", "Operations", "31 Oct · overdue", true, false),
                new ActionRow("Runbook", "R. Lim", "Engineering", "—", false, true))));
        assertThat(of(p, "releases")).isEmpty();
        assertThat(of(p, "incidents").get(0).data().get("empty")).isEqualTo("Not provided yet.");
        Slide a = of(p, "actions").get(0);
        assertThat(a.data().get("rowFlags")).isEqualTo(List.of("overdue", "closed"));
        assertThat(p.slides().get(p.slides().size() - 1).data().get("body")).isEqualTo("1 open action item carried to next month.");
    }
}
