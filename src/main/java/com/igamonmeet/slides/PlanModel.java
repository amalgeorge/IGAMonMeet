package com.igamonmeet.slides;

import com.fasterxml.jackson.databind.JsonNode;
import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.template.OverflowRule;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Inputs and output of the slide engine. Slides are output only: users edit Sections, never slides. */
public final class PlanModel {

    private PlanModel() {
    }

    public record Series(List<String> labels, List<BigDecimal> values, BigDecimal target, String unit) {
    }

    public record SectionInput(String key, String name, TeamTag team, SectionKind kind, String layout, OverflowRule rule,
                               boolean required, boolean filled, List<String> columns, JsonNode content, List<KpiView> kpis,
                               Series chart, SectionGroup group, String kpiLayout, JsonNode tableStyle) {

        public SectionInput(String key, String name, TeamTag team, SectionKind kind, String layout, OverflowRule rule,
                            boolean required, boolean filled, List<String> columns, JsonNode content, List<KpiView> kpis, Series chart,
                            SectionGroup group, String kpiLayout) {
            this(key, name, team, kind, layout, rule, required, filled, columns, content, kpis, chart, group, kpiLayout, null);
        }

        public SectionInput(String key, String name, TeamTag team, SectionKind kind, String layout, OverflowRule rule,
                            boolean required, boolean filled, List<String> columns, JsonNode content, List<KpiView> kpis, Series chart,
                            SectionGroup group) {
            this(key, name, team, kind, layout, rule, required, filled, columns, content, kpis, chart, group, null, null);
        }

        public SectionInput(String key, String name, TeamTag team, SectionKind kind, String layout, OverflowRule rule,
                            boolean required, boolean filled, List<String> columns, JsonNode content, List<KpiView> kpis, Series chart) {
            this(key, name, team, kind, layout, rule, required, filled, columns, content, kpis, chart, null, null, null);
        }
    }

    /** The Section group a Section sits in (spec-v2 §1.5); null when it has none. */
    public record SectionGroup(String key, String name, boolean divider) {
    }

    public record ActionRow(String title, String owner, String team, String due, boolean overdue, boolean closed) {
    }

    /** Placeholder boxes from the House template theme, in points. */
    public record Box(double w, double h) {
    }

    public record Input(String title, String monthLabel, String dateLabel, List<SectionInput> sections, List<ActionRow> actions,
                        double slideWidth, double slideHeight, Map<String, Box> boxes) {
    }

    /** {@code data} is layout-specific; see SlidePlanner for each shape. */
    public record Slide(int n, String layout, String sectionKey, String part, boolean continuation, boolean appendix,
                        Map<String, Object> data) {
    }

    public record Plan(List<Slide> slides, Map<String, Integer> slidesPerSection, String font) {
    }
}
