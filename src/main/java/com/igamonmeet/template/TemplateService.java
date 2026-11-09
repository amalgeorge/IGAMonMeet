package com.igamonmeet.template;

import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.template.TemplateDtos.Draft;
import com.igamonmeet.template.TemplateDtos.Group;
import com.igamonmeet.template.TemplateDtos.LibraryKpi;
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
        if (draft.groups() == null) {
            // Groups left out means "keep the current ones" ([] removes them all).
            draft = new Draft(draft.basedOnVersion(), draft.note(), draft.sections(), TemplateDtos.of(current()).groups(), draft.kpiLibrary());
        }
        java.util.Map<String, LibraryKpi> library = library(draft);
        validate(draft.sections(), draft.groupsOrEmpty(), library);

        TemplateVersion v = new TemplateVersion(current + 1, admin, clock.instant(), draft.note());
        int g = 0;
        for (Group grp : draft.groupsOrEmpty()) {
            v.add(new TemplateGroup(v, grp.key(), grp.name().trim(), grp.visibilityOrDefault(), grp.dividerOrDefault(), ++g));
        }
        int d = 0;
        for (LibraryKpi lk : library.values()) {
            TemplateKpiDef def = new TemplateKpiDef(v, lk.key(), lk.name().trim(), lk.unit().trim(), lk.target(), lk.direction(), lk.amberThreshold(), ++d);
            def.setValueSpec(lk.valueTypeOrDefault(), lk.currency(), KpiFormat.optionsJson(lk.statusOptions()));
            def.setDisplay(lk.subtitle(), lk.description(), lk.decimals());
            v.add(def);
        }
        int order = 0;
        for (Section s : draft.sections()) {
            String cols = s.kind() == SectionKind.TABLE ? String.join("|", s.columns()) : null;
            TemplateSection ts = new TemplateSection(v, s.key(), s.name().trim(), s.teamTag(), s.kind(), s.layout(), s.overflowRule(),
                    s.required(), cols, s.kind() == SectionKind.CHART ? s.chartKpiKey() : null, ++order);
            ts.setVisibility(s.visibilityOrDefault(), s.onSlidesOrDefault());
            ts.setGroupKey(s.groupKey() == null || s.groupKey().isBlank() ? null : s.groupKey());
            ts.setTableStyle(s.kind() == SectionKind.TABLE && s.tableStyle() != null && !s.tableStyle().isNull() ? s.tableStyle().toString() : null);
            ts.setKpiLayout(s.kind() == SectionKind.KPI && s.kpiLayout() != null && !"4".equals(s.kpiLayout()) ? s.kpiLayout() : null);
            int k = 0;
            if (s.kind() == SectionKind.KPI) {
                for (Kpi kpi : s.kpis()) {
                    LibraryKpi def = library.get(kpi.key()); // the placement copies the library definition
                    TemplateKpi tk = new TemplateKpi(ts, kpi.key(), def.name().trim(), def.unit().trim(), def.target(), def.direction(),
                            def.amberThreshold(), kpi.required(), ++k);
                    tk.setVisibility(kpi.visibilityOrDefault());
                    tk.setValueSpec(def.valueTypeOrDefault(), def.currency(), KpiFormat.optionsJson(def.statusOptions()));
                    tk.setDisplay(def.subtitle(), def.description(), def.decimals());
                    ts.add(tk);
                }
            }
            v.add(ts);
        }
        versions.save(v);
        audit.record("TEMPLATE_PUBLISHED", "Template", v.getVersionNo(), draft.note());
        return v;
    }

    /** The draft's KPI library; KPIs placed in Sections but missing from it (older clients) are added from the placement. */
    private static java.util.Map<String, LibraryKpi> library(Draft draft) {
        java.util.Map<String, LibraryKpi> lib = new java.util.LinkedHashMap<>();
        if (draft.kpiLibrary() != null) {
            for (LibraryKpi k : draft.kpiLibrary()) {
                check(lib.putIfAbsent(k.key(), k) == null, "KPI '" + k.key() + "' is in the library twice.");
            }
        }
        for (Section s : draft.sections()) {
            if (s.kind() == SectionKind.KPI && s.kpis() != null) {
                for (Kpi k : s.kpis()) {
                    lib.putIfAbsent(k.key(), new LibraryKpi(k.key(), k.name(), k.unit(), k.target(), k.direction(), k.amberThreshold()));
                }
            }
        }
        return lib;
    }

    private static void validate(List<Section> sections, List<Group> groups, java.util.Map<String, LibraryKpi> library) {
        for (LibraryKpi k : library.values()) {
            if (k.valueTypeOrDefault() == ValueType.TEXT_STATUS) {
                List<KpiFormat.StatusOption> opts = k.statusOptions() == null ? List.of() : k.statusOptions();
                check(!opts.isEmpty(), "Text status KPI '" + k.name() + "' needs at least one option.");
                Set<String> labels = new HashSet<>();
                for (KpiFormat.StatusOption o : opts) {
                    check(o.label() != null && !o.label().isBlank() && o.label().length() <= 40 && o.status() != null,
                            "Each option of '" + k.name() + "' needs a label (up to 40 characters) and a status.");
                    check(labels.add(o.label().trim().toLowerCase()), "Option '" + o.label() + "' is listed twice in '" + k.name() + "'.");
                }
                continue; // no target or amber for text statuses
            }
            boolean amberOk = k.direction() == Direction.HIGHER
                    ? k.amberThreshold().compareTo(k.target()) <= 0
                    : k.amberThreshold().compareTo(k.target()) >= 0;
            check(amberOk, "KPI '" + k.name() + "': the amber threshold must be on the "
                    + (k.direction() == Direction.HIGHER ? "lower" : "higher") + " side of the target.");
        }
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
                check(s.columns().size() <= 12, "Table section '" + s.name() + "' can have up to 12 columns.");
                s.columns().forEach(c -> check(c != null && !c.isBlank() && c.length() <= 80 && !c.contains("|"),
                        "Each column of '" + s.name() + "' needs a name of up to 80 characters, without '|'."));
                String styleProblem = com.igamonmeet.slides.TableStyle.problem(s.tableStyle(), s.columns().size());
                check(styleProblem == null, "Table section '" + s.name() + "': " + styleProblem);
            }
            if (s.kind() == SectionKind.KPI) {
                check(s.kpis() != null && !s.kpis().isEmpty(), "KPI section '" + s.name() + "' needs at least one KPI.");
                check(s.visibilityOrDefault() != Visibility.SHOWN || s.kpis().stream().anyMatch(k -> k.visibilityOrDefault() == Visibility.SHOWN),
                        "Shown KPI section '" + s.name() + "' needs at least one Shown KPI.");
                Set<String> sectionKpiKeys = new HashSet<>();
                for (Kpi k : s.kpis()) {
                    check(sectionKpiKeys.add(k.key()), "KPI '" + k.key() + "' is placed twice in '" + s.name() + "'.");
                    kpiKeys.add(k.key());
                    if (k.visibilityOrDefault() != Visibility.RETIRED) {
                        liveKpiKeys.add(k.key());
                    }
                }
            }
        }
        for (Section s : sections) {
            if (s.kind() == SectionKind.CHART) {
                check(s.chartKpiKey() != null && kpiKeys.contains(s.chartKpiKey()),
                        "Chart section '" + s.name() + "' must show one of the template's KPIs.");
                check(library.get(s.chartKpiKey()) == null || library.get(s.chartKpiKey()).valueTypeOrDefault().numeric(),
                        "Chart section '" + s.name() + "' cannot chart a Text status KPI.");
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
