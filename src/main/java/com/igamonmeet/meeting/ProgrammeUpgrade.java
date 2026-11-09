package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.settings.Settings;
import com.igamonmeet.template.Direction;
import com.igamonmeet.template.OverflowRule;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import com.igamonmeet.template.TemplateDtos;
import com.igamonmeet.template.TemplateDtos.Draft;
import com.igamonmeet.template.TemplateDtos.Group;
import com.igamonmeet.template.TemplateDtos.Kpi;
import com.igamonmeet.template.TemplateDtos.LibraryKpi;
import com.igamonmeet.template.TemplateDtos.Section;
import com.igamonmeet.template.TemplateService;
import com.igamonmeet.template.TemplateVersion;
import com.igamonmeet.template.ValueType;
import com.igamonmeet.template.Visibility;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-time upgrade (docs/spec-v3.md §3): adds the "Programme" Section group with six Available Sections and the
 * Privileged ID KPIs to the Template. Nothing is switched on in any Meeting; editors switch on what they use.
 * Existing Sections or KPIs with the same keys are left alone. Runs once; marked by UPGRADE_PROGRAMME_DONE.
 */
@Component
@Order(20)
public class ProgrammeUpgrade implements ApplicationRunner {

    static final String FLAG = "UPGRADE_PROGRAMME_DONE";
    public static final String GROUP = "programme";
    private static final Logger log = LoggerFactory.getLogger(ProgrammeUpgrade.class);

    private final Settings settings;
    private final TemplateService templates;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public ProgrammeUpgrade(Settings settings, TemplateService templates, ObjectMapper json, TransactionTemplate tx) {
        this.settings = settings;
        this.templates = templates;
        this.json = json;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.flag(FLAG)) return;
        upgrade();
        log.info("Programme Sections upgrade done");
    }

    /** Public so tests can re-apply it after other tests replace the Template. */
    public void upgrade() {
        tx.executeWithoutResult(status -> {
            TemplateVersion current = templates.current();
            Draft base = TemplateDtos.draftOf(current, "Programme Sections (v3 upgrade)");
            Set<String> keys = base.sections().stream().map(Section::key).collect(Collectors.toSet());
            List<Section> add = sections().stream().filter(s -> !keys.contains(s.key())).toList();
            if (!add.isEmpty()) {
                List<Section> secs = new ArrayList<>(base.sections());
                int at = secs.size();
                for (int i = 0; i < secs.size(); i++) {
                    if (secs.get(i).kind() == SectionKind.ACTION_ITEMS) { at = i; break; } // keep Action items last
                }
                secs.addAll(at, add);
                List<Group> groups = new ArrayList<>(base.groupsOrEmpty());
                if (groups.stream().noneMatch(g -> g.key().equals(GROUP))) {
                    groups.add(new Group(GROUP, "Programme", Visibility.AVAILABLE, true));
                }
                List<LibraryKpi> library = new ArrayList<>(base.kpiLibrary() == null ? List.of() : base.kpiLibrary());
                Set<String> libKeys = library.stream().map(LibraryKpi::key).collect(Collectors.toSet());
                libraryKpis().stream().filter(k -> !libKeys.contains(k.key())).forEach(library::add);
                templates.publish(new Draft(base.basedOnVersion(), base.note(), secs, groups, library), "system");
            }
            settings.setFlag(FLAG, "system");
        });
    }

    private static List<LibraryKpi> libraryKpis() {
        // Targets are starting points; the Admin adjusts them in the KPI library.
        return List.of(
                new LibraryKpi("vault-coverage", "Privileged accounts vaulted", "%", new BigDecimal("95"), Direction.HIGHER, new BigDecimal("90"),
                        ValueType.NUMBER, null, null, null, "Share of privileged accounts managed in the vault.", null),
                new LibraryKpi("sessions-recorded", "Privileged sessions recorded", "%", new BigDecimal("95"), Direction.HIGHER, new BigDecimal("90"),
                        ValueType.NUMBER, null, null, null, "Share of privileged sessions recorded.", null),
                new LibraryKpi("break-glass", "Break-glass uses", "uses", BigDecimal.ZERO, Direction.LOWER, new BigDecimal("2"),
                        ValueType.COUNT, null, null, null, "Emergency access uses this month; each one is reviewed.", null));
    }

    private List<Section> sections() {
        List<Kpi> pid = libraryKpis().stream().map(k -> new Kpi(k.key(), k.name(), k.unit(), k.target(), k.direction(), k.amberThreshold(),
                false, Visibility.SHOWN)).toList();
        return List.of(
                table("access-reviews", "Access reviews", List.of("Campaign", "Scope", "% complete", "Due", "Overdue reviewers", "Status"),
                        style().put("statusColumn", 5).set("wholeNumbers", json.createArrayNode().add(4))),
                new Section("privileged-id", "Privileged ID", TeamTag.OPERATIONS, SectionKind.KPI, "IAM_KPI", OverflowRule.CONTINUE, false,
                        List.of(), null, pid, Visibility.AVAILABLE, true, GROUP, "3", null),
                table("jml", "Joiner-Mover-Leaver", List.of("Type", "Requests", "Completed"),
                        style().put("total", true).set("wholeNumbers", json.createArrayNode().add(1).add(2))),
                table("app-onboarding", "Application onboarding pipeline", List.of("Application", "Stage", "ETA", "Blocker", "Status"),
                        style().put("statusColumn", 4)),
                table("decisions", "Decisions needed", List.of("Decision", "Context", "Needed by", "Owner"), null),
                new Section("wins", "Wins and recognition", TeamTag.SHARED, SectionKind.TEXT, "IAM_SUMMARY", OverflowRule.CONTINUE, false,
                        List.of(), null, List.of(), Visibility.AVAILABLE, true, GROUP, null, null));
    }

    private Section table(String key, String name, List<String> columns, ObjectNode style) {
        return new Section(key, name, TeamTag.SHARED, SectionKind.TABLE, "IAM_TABLE", OverflowRule.CONTINUE, false,
                columns, null, List.of(), Visibility.AVAILABLE, true, GROUP, null, style);
    }

    private ObjectNode style() {
        return json.createObjectNode();
    }
}
