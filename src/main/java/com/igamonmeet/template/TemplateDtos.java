package com.igamonmeet.template;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** JSON shapes for the Template API. */
public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record Kpi(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,60}") String key,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Size(max = 20) String unit,
            @NotNull BigDecimal target,
            @NotNull Direction direction,
            @NotNull BigDecimal amberThreshold,
            boolean required,
            Visibility visibility) {

        public Visibility visibilityOrDefault() { return visibility == null ? Visibility.SHOWN : visibility; }
    }

    public record Section(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,60}") String key,
            @NotBlank @Size(max = 200) String name,
            @NotNull TeamTag teamTag,
            @NotNull SectionKind kind,
            @NotBlank String layout,
            @NotNull OverflowRule overflowRule,
            boolean required,
            List<@NotBlank @Size(max = 100) String> columns,
            String chartKpiKey,
            List<@Valid Kpi> kpis,
            Visibility visibility,
            Boolean onSlides,
            String groupKey,
            @Pattern(regexp = "2|3|4|6|8|TABLE") String kpiLayout,
            com.fasterxml.jackson.databind.JsonNode tableStyle) {

        public Visibility visibilityOrDefault() { return visibility == null ? Visibility.SHOWN : visibility; }

        public boolean onSlidesOrDefault() { return onSlides == null || onSlides; }
    }

    public record Group(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,60}") String key,
            @NotBlank @Size(max = 200) String name,
            Visibility visibility,
            Boolean divider) {

        public Visibility visibilityOrDefault() { return visibility == null ? Visibility.SHOWN : visibility; }

        public boolean dividerOrDefault() { return divider == null || divider; }
    }

    /** A KPI definition in the library; Sections place KPIs from it by key. */
    public record LibraryKpi(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,60}") String key,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Size(max = 20) String unit,
            @NotNull BigDecimal target,
            @NotNull Direction direction,
            @NotNull BigDecimal amberThreshold,
            ValueType valueType,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            List<KpiFormat.StatusOption> statusOptions,
            @Size(max = 200) String subtitle,
            @Size(max = 1000) String description,
            @javax.validation.constraints.Min(0) @javax.validation.constraints.Max(4) Integer decimals) {

        public LibraryKpi(String key, String name, String unit, BigDecimal target, Direction direction, BigDecimal amberThreshold) {
            this(key, name, unit, target, direction, amberThreshold, null, null, null, null, null, null);
        }

        public ValueType valueTypeOrDefault() { return valueType == null ? ValueType.NUMBER : valueType; }
    }

    /** What an Admin publishes: the full edited Template, based on the version they started from. */
    public record Draft(int basedOnVersion, @Size(max = 500) String note, @NotEmpty List<@Valid Section> sections, List<@Valid Group> groups,
                        List<@Valid LibraryKpi> kpiLibrary) {

        public List<Group> groupsOrEmpty() { return groups == null ? List.of() : groups; }
    }

    public record Version(int versionNo, String createdBy, Instant createdAt, String note, List<Section> sections, List<Group> groups, List<LibraryKpi> kpiLibrary) {
    }

    public record VersionSummary(int versionNo, String createdBy, Instant createdAt, String note, int sectionCount) {
    }

    static Version of(TemplateVersion v) {
        return new Version(v.getVersionNo(), v.getCreatedBy(), v.getCreatedAt(), v.getNote(),
                v.getSections().stream().map(TemplateDtos::of).collect(Collectors.toList()),
                v.getGroups().stream().map(g -> new Group(g.getGroupKey(), g.getName(), g.getVisibility(), g.isDivider())).collect(Collectors.toList()),
                v.getKpiLibrary().stream().map(d -> new LibraryKpi(d.getKpiKey(), d.getName(), d.getUnit(), d.getTarget(), d.getDirection(),
                        d.getAmberThreshold(), d.getValueType(), d.getCurrency(), d.getStatusOptions(), d.getSubtitle(), d.getDescription(),
                        d.getDecimals())).collect(Collectors.toList()));
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    static com.fasterxml.jackson.databind.JsonNode readJson(String s) {
        try {
            return s == null ? null : JSON.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    static Section of(TemplateSection s) {
        List<String> cols = s.getColumnsSpec() == null ? List.of() : Arrays.asList(s.getColumnsSpec().split("\\|"));
        List<Kpi> kpis = s.getKpis().stream()
                .map(k -> new Kpi(k.getKpiKey(), k.getName(), k.getUnit(), k.getTarget(), k.getDirection(), k.getAmberThreshold(), k.isRequired(), k.getVisibility()))
                .collect(Collectors.toList());
        return new Section(s.getSectionKey(), s.getName(), s.getTeamTag(), s.getKind(), s.getLayout(), s.getOverflowRule(),
                s.isRequired(), cols, s.getChartKpiKey(), kpis, s.getVisibility(), s.isOnSlides(), s.getGroupKey(), s.getKpiLayout(),
                readJson(s.getTableStyle()));
    }
}
