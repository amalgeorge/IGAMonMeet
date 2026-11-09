package com.igamonmeet.template;

import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.template.TemplateDtos.Draft;
import com.igamonmeet.template.TemplateDtos.Group;
import com.igamonmeet.template.TemplateDtos.Kpi;
import com.igamonmeet.template.TemplateDtos.Section;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private final TemplateVersionRepository versions;
    private final AuditLog audit;
    private final Clock clock;

    public TemplateService(TemplateVersionRepository versions, AuditLog audit, Clock clock) {
        this.versions = versions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TemplateVersion current() {
        return versions.findFirstByOrderByVersionNoDesc().orElseThrow();
    }

    @Transactional(readOnly = true)
    public TemplateVersion version(int versionNo) {
        return versions.findByVersionNo(versionNo)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such template version."));
    }

    /** Validates the draft and stores it as the next version. Earlier versions never change. */
    @Transactional
    public TemplateVersion publish(Draft draft, String admin) {
        int current = current().getVersionNo();
        if (draft.basedOnVersion() != current) {
            throw new AccountException(HttpStatus.CONFLICT, "TEMPLATE_CHANGED",
                    "Someone published version " + current + " while you were editing. Reload and apply your changes again.");
        }
        validate(draft.sections(), draft.groupsOrEmpty());

        TemplateVersion v = new TemplateVersion(current + 1, admin, clock.instant(), draft.note());
        int g = 0;
        for (Group grp : draft.groupsOrEmpty()) {
            v.add(new TemplateGroup(v, grp.key(), grp.name().trim(), grp.visibilityOrDefault(), grp.dividerOrDefault(), ++g));
        }
        int order = 0;
        for (Section s : draft.sections()) {
            String cols = s.kind() == SectionKind.TABLE ? String.join("|", s.columns()) : null;
            TemplateSection ts = new TemplateSection(v, s.key(), s.name().trim(), s.teamTag(), s.kind(), s.layout(), s.overflowRule(),
                    s.required(), cols, s.kind() == SectionKind.CHART ? s.chartKpiKey() : null, ++order);
            ts.setVisibility(s.visibilityOrDefault(), s.onSlidesOrDefault());
            ts.setGroupKey(s.groupKey() == null || s.groupKey().isBlank() ? null : s.groupKey());
            int k = 0;
            if (s.kind() == SectionKind.KPI) {
                for (Kpi kpi : s.kpis()) {
                    TemplateKpi tk = new TemplateKpi(ts, kpi.key(), kpi.name().trim(), kpi.unit().trim(), kpi.target(), kpi.direction(),
                            kpi.amberThreshold(), kpi.required(), ++k);
                    tk.setVisibility(kpi.visibilityOrDefault());
                    ts.add(tk);
                }
            }
            v.add(ts);
        }
        versions.save(v);
        audit.record("TEMPLATE_PUBLISHED", "Template", v.getVersionNo(), draft.note());
        return v;
    }

    private static void validate(List<Section> sections, List<Group> groups) {
        Set<String> groupKeys = new HashSet<>();
        for (Group g : groups) {
            check(groupKeys.add(g.key()), "Group key '" + g.key() + "' is used twice.");
        }
        Set<String> closed = new HashSet<>();
        String previous = null;
        for (Section s : sections) {
            String gk = s.groupKey() == null || s.groupKey().isBlank() ? null : s.groupKey();
            check(gk == null || groupKeys.contains(gk), "Section '" + s.name() + "' is in group '" + gk + "', which does not exist.");
            if (previous != null && !previous.equals(gk)) {
                closed.add(previous);
            }
            check(gk == null || !closed.contains(gk), "The Sections of a group must be next to each other ('" + s.name() + "').");
            previous = gk;
        }
        Set<String> sectionKeys = new HashSet<>();
        Set<String> kpiKeys = new HashSet<>();
        Set<String> liveKpiKeys = new HashSet<>();
        check(sections.stream().anyMatch(s -> s.visibilityOrDefault() == Visibility.SHOWN),
                "At least one Section must be Shown.");
        for (Section s : sections) {
            check(sectionKeys.add(s.key()), "Section key '" + s.key() + "' is used twice.");
            check(s.kind().allows(s.layout()), "Layout " + s.layout() + " cannot show a " + s.kind() + " section ('" + s.name() + "').");
            if (s.kind() == SectionKind.TABLE) {
                check(s.columns() != null && !s.columns().isEmpty(), "Table section '" + s.name() + "' needs at least one column.");
                s.columns().forEach(c -> check(!c.contains("|"), "Column names cannot contain '|'."));
            }
            if (s.kind() == SectionKind.KPI) {
                check(s.kpis() != null && !s.kpis().isEmpty(), "KPI section '" + s.name() + "' needs at least one KPI.");
                check(s.visibilityOrDefault() != Visibility.SHOWN || s.kpis().stream().anyMatch(k -> k.visibilityOrDefault() == Visibility.SHOWN),
                        "Shown KPI section '" + s.name() + "' needs at least one Shown KPI.");
                for (Kpi k : s.kpis()) {
                    check(kpiKeys.add(k.key()), "KPI key '" + k.key() + "' is used twice.");
                    if (k.visibilityOrDefault() != Visibility.RETIRED) {
                        liveKpiKeys.add(k.key());
                    }
                    boolean amberOk = k.direction() == Direction.HIGHER
                            ? k.amberThreshold().compareTo(k.target()) <= 0
                            : k.amberThreshold().compareTo(k.target()) >= 0;
                    check(amberOk, "KPI '" + k.name() + "': the amber threshold must be on the "
                            + (k.direction() == Direction.HIGHER ? "lower" : "higher") + " side of the target.");
                }
            }
        }
        for (Section s : sections) {
            if (s.kind() == SectionKind.CHART) {
                check(s.chartKpiKey() != null && kpiKeys.contains(s.chartKpiKey()),
                        "Chart section '" + s.name() + "' must show one of the template's KPIs.");
                check(s.visibilityOrDefault() == Visibility.RETIRED || liveKpiKeys.contains(s.chartKpiKey()),
                        "Chart section '" + s.name() + "' shows a Retired KPI. Pick another KPI or retire the chart too.");
            }
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE", message);
        }
    }
}
