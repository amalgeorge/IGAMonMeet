package com.igamonmeet.template;

import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.template.TemplateDtos.Draft;
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
        validate(draft.sections());

        TemplateVersion v = new TemplateVersion(current + 1, admin, clock.instant(), draft.note());
        int order = 0;
        for (Section s : draft.sections()) {
            String cols = s.kind() == SectionKind.TABLE ? String.join("|", s.columns()) : null;
            TemplateSection ts = new TemplateSection(v, s.key(), s.name().trim(), s.teamTag(), s.kind(), s.layout(), s.overflowRule(),
                    s.required(), cols, s.kind() == SectionKind.CHART ? s.chartKpiKey() : null, ++order);
            int k = 0;
            if (s.kind() == SectionKind.KPI) {
                for (Kpi kpi : s.kpis()) {
                    ts.add(new TemplateKpi(ts, kpi.key(), kpi.name().trim(), kpi.unit().trim(), kpi.target(), kpi.direction(),
                            kpi.amberThreshold(), kpi.required(), ++k));
                }
            }
            v.add(ts);
        }
        versions.save(v);
        audit.record("TEMPLATE_PUBLISHED", "Template", v.getVersionNo(), draft.note());
        return v;
    }

    private static void validate(List<Section> sections) {
        Set<String> sectionKeys = new HashSet<>();
        Set<String> kpiKeys = new HashSet<>();
        for (Section s : sections) {
            check(sectionKeys.add(s.key()), "Section key '" + s.key() + "' is used twice.");
            check(s.kind().allows(s.layout()), "Layout " + s.layout() + " cannot show a " + s.kind() + " section ('" + s.name() + "').");
            if (s.kind() == SectionKind.TABLE) {
                check(s.columns() != null && !s.columns().isEmpty(), "Table section '" + s.name() + "' needs at least one column.");
                s.columns().forEach(c -> check(!c.contains("|"), "Column names cannot contain '|'."));
            }
            if (s.kind() == SectionKind.KPI) {
                check(s.kpis() != null && !s.kpis().isEmpty(), "KPI section '" + s.name() + "' needs at least one KPI.");
                for (Kpi k : s.kpis()) {
                    check(kpiKeys.add(k.key()), "KPI key '" + k.key() + "' is used twice.");
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
            }
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE", message);
        }
    }
}
