package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.settings.Settings;
import com.igamonmeet.template.OverflowRule;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TemplateDtos;
import com.igamonmeet.template.TemplateDtos.Draft;
import com.igamonmeet.template.TemplateDtos.Section;
import com.igamonmeet.template.TemplateSection;
import com.igamonmeet.template.TemplateService;
import com.igamonmeet.template.TemplateVersion;
import com.igamonmeet.template.TeamTag;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-time upgrade (docs/spec-v3.md §1.3): the Template's Incidents Section becomes SITREP, and Preparing Meetings
 * swap their Incidents Section for an empty SITREP one. Held Meetings keep Incidents (ADR 0008). The old Section of a
 * Preparing Meeting is hidden, not deleted, so its rows stay on the Meeting. Runs once; marked by UPGRADE_SITREP_DONE.
 */
@Component
public class SitrepUpgrade implements ApplicationRunner {

    static final String FLAG = "UPGRADE_SITREP_DONE";
    public static final String KEY = "sitrep";
    public static final List<String> COLUMNS = List.of("Type", "New", "Closed", "Pending", "Above SLA");
    private static final Logger log = LoggerFactory.getLogger(SitrepUpgrade.class);

    private final Settings settings;
    private final TemplateService templates;
    private final MeetingRepository meetings;
    private final MeetingSectionRepository sections;
    private final MeetingTime time;
    private final AuditLog audit;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public SitrepUpgrade(Settings settings, TemplateService templates, MeetingRepository meetings, MeetingSectionRepository sections,
                         MeetingTime time, AuditLog audit, ObjectMapper json, TransactionTemplate tx) {
        this.settings = settings;
        this.templates = templates;
        this.meetings = meetings;
        this.sections = sections;
        this.time = time;
        this.audit = audit;
        this.json = json;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.flag(FLAG)) return;
        upgrade();
        log.info("SITREP upgrade done");
    }

    /** Normally called once at start-up; public so tests can put SITREP back after other tests replace the Template. */
    public void upgrade() {
        tx.executeWithoutResult(status -> doUpgrade());
    }

    private void doUpgrade() {
        settings.lockFlag(FLAG); // one server at a time
        TemplateVersion current = templates.current();
        boolean hasSitrep = current.getSections().stream().anyMatch(s -> s.getSectionKey().equals(KEY));
        Optional<TemplateSection> incidents = current.getSections().stream().filter(s -> s.getSectionKey().equals("incidents")).findFirst();
        TemplateVersion target = current;
        if (!hasSitrep) {
            Draft draft = TemplateDtos.draftOf(current, "SITREP replaces Incidents (v3 upgrade)");
            List<Section> secs = draft.sections();
            int at = secs.size();
            Section old = null;
            for (int i = 0; i < secs.size(); i++) {
                if (secs.get(i).key().equals("incidents")) { at = i; old = secs.get(i); }
            }
            Section sitrep = new Section(KEY, "SITREP", old == null ? TeamTag.OPERATIONS : old.teamTag(), SectionKind.TABLE, "IAM_TABLE",
                    old == null ? OverflowRule.CONTINUE : old.overflowRule(), old != null && old.required(), COLUMNS, null, List.of(),
                    old == null ? null : old.visibility(), old == null ? null : old.onSlides(), old == null ? null : old.groupKey(), null, style());
            if (old != null) secs.set(at, sitrep);
            else secs.add(sitrep);
            target = templates.publish(draft, "system");
        }
        int moved = 0;
        if (incidents.isPresent() || !hasSitrep) {
            for (Meeting m : meetings.findByDeletedAtIsNullOrderByMeetingDateDesc()) {
                if (!"PREPARING".equals(time.label(m.getMeetingDate()))) continue;   // Held Meetings keep Incidents
                if (m.getSections().stream().anyMatch(s -> s.getSectionKey().equals(KEY))) continue;
                Optional<MeetingSection> inc = m.getSections().stream()
                        .filter(s -> s.getSectionKey().equals("incidents") && !s.isHidden()).findFirst();
                if (inc.isEmpty()) continue;
                MeetingSection old = inc.get();
                TemplateSection def = target.getSections().stream().filter(s -> s.getSectionKey().equals(KEY)).findFirst().orElseThrow();
                MeetingSection n = new MeetingSection(m, KEY, def.getName(), def.getTeamTag(), def.getKind(), def.getLayout(),
                        def.getOverflowRule(), def.isRequired(), def.getColumnsSpec(), null, old.getSortOrder());
                n.setOnSlides(def.isOnSlides());
                n.pinTo(target.getVersionNo());
                old.setHidden(true); // rows kept on the Meeting
                m.add(n);
                sections.saveAll(m.getSections());
                audit.record("SECTION_REPLACED", "Meeting", m.getId(), "incidents -> sitrep (v3 upgrade)");
                moved++;
            }
        }
        settings.setFlag(FLAG, "system");
        if (moved > 0) log.info("SITREP: {} Preparing Meeting(s) moved from Incidents", moved);
    }

    /** Whole-number counts, a total row, Above SLA red when above 0, Type suggests the Admin's SITREP types. */
    private ObjectNode style() {
        ObjectNode s = json.createObjectNode();
        s.putArray("align").add("left").add("right").add("right").add("right").add("right");
        s.putArray("wholeNumbers").add(1).add(2).add(3).add(4);
        s.put("total", true);
        s.put("alertColumn", 4);
        s.put("listColumn", 0);
        return s;
    }
}
