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
            String groupKey) {

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

    /** What an Admin publishes: the full edited Template, based on the version they started from. */
    public record Draft(int basedOnVersion, @Size(max = 500) String note, @NotEmpty List<@Valid Section> sections, List<@Valid Group> groups) {

        public List<Group> groupsOrEmpty() { return groups == null ? List.of() : groups; }
    }

    public record Version(int versionNo, String createdBy, Instant createdAt, String note, List<Section> sections, List<Group> groups) {
    }

    public record VersionSummary(int versionNo, String createdBy, Instant createdAt, String note, int sectionCount) {
    }

    static Version of(TemplateVersion v) {
        return new Version(v.getVersionNo(), v.getCreatedBy(), v.getCreatedAt(), v.getNote(),
                v.getSections().stream().map(TemplateDtos::of).collect(Collectors.toList()),
                v.getGroups().stream().map(g -> new Group(g.getGroupKey(), g.getName(), g.getVisibility(), g.isDivider())).collect(Collectors.toList()));
    }

    static Section of(TemplateSection s) {
        List<String> cols = s.getColumnsSpec() == null ? List.of() : Arrays.asList(s.getColumnsSpec().split("\\|"));
        List<Kpi> kpis = s.getKpis().stream()
                .map(k -> new Kpi(k.getKpiKey(), k.getName(), k.getUnit(), k.getTarget(), k.getDirection(), k.getAmberThreshold(), k.isRequired(), k.getVisibility()))
                .collect(Collectors.toList());
        return new Section(s.getSectionKey(), s.getName(), s.getTeamTag(), s.getKind(), s.getLayout(), s.getOverflowRule(),
                s.isRequired(), cols, s.getChartKpiKey(), kpis, s.getVisibility(), s.isOnSlides(), s.getGroupKey());
    }
}
