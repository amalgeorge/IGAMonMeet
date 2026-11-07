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
            boolean required) {
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
            List<@Valid Kpi> kpis) {
    }

    /** What an Admin publishes: the full edited Template, based on the version they started from. */
    public record Draft(int basedOnVersion, @Size(max = 500) String note, @NotEmpty List<@Valid Section> sections) {
    }

    public record Version(int versionNo, String createdBy, Instant createdAt, String note, List<Section> sections) {
    }

    public record VersionSummary(int versionNo, String createdBy, Instant createdAt, String note, int sectionCount) {
    }

    static Version of(TemplateVersion v) {
        return new Version(v.getVersionNo(), v.getCreatedBy(), v.getCreatedAt(), v.getNote(),
                v.getSections().stream().map(TemplateDtos::of).collect(Collectors.toList()));
    }

    static Section of(TemplateSection s) {
        List<String> cols = s.getColumnsSpec() == null ? List.of() : Arrays.asList(s.getColumnsSpec().split("\\|"));
        List<Kpi> kpis = s.getKpis().stream()
                .map(k -> new Kpi(k.getKpiKey(), k.getName(), k.getUnit(), k.getTarget(), k.getDirection(), k.getAmberThreshold(), k.isRequired()))
                .collect(Collectors.toList());
        return new Section(s.getSectionKey(), s.getName(), s.getTeamTag(), s.getKind(), s.getLayout(), s.getOverflowRule(),
                s.isRequired(), cols, s.getChartKpiKey(), kpis);
    }
}
