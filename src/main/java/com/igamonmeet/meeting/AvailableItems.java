package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TemplateKpi;
import com.igamonmeet.template.TemplateSection;
import com.igamonmeet.template.TemplateService;
import com.igamonmeet.template.TemplateVersion;
import com.igamonmeet.template.Visibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editors switch Available Sections and KPIs on or off for one Meeting (docs/spec-v2.md §1.3). The catalogue is the
 * latest Template version's Available items while the Meeting is Preparing; Held Meetings stay as they are (ADR 0008).
 */
@Service
public class AvailableItems {

    public record CatalogueSection(String key, String name, SectionKind kind) {
    }

    public record CatalogueKpi(String sectionKey, String key, String name) {
    }

    public record Catalogue(boolean open, List<CatalogueSection> sections, List<CatalogueKpi> kpis) {
    }

    private final MeetingService meetings;
    private final SectionEditor editor;
    private final TemplateService templates;
    private final SectionDefs defs;
    private final MeetingSectionRepository sections;
    private final MeetingTime time;
    private final AuditLog audit;
    private final ObjectMapper json;

    public AvailableItems(MeetingService meetings, SectionEditor editor, TemplateService templates, SectionDefs defs,
                          MeetingSectionRepository sections, MeetingTime time, AuditLog audit, ObjectMapper json) {
        this.meetings = meetings;
        this.editor = editor;
        this.templates = templates;
        this.defs = defs;
        this.sections = sections;
        this.time = time;
        this.audit = audit;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Catalogue catalogue(long meetingId, boolean admin) {
        Meeting m = meetings.live(meetingId);
        if (!preparing(m) || !(admin || time.editWindowOpen(m.getMeetingDate()))) {
            return new Catalogue(false, List.of(), List.of());
        }
        TemplateVersion latest = templates.current();
        Set<String> visible = m.getSections().stream().filter(s -> !s.isHidden()).map(MeetingSection::getSectionKey).collect(Collectors.toSet());
        List<CatalogueSection> secs = latest.getSections().stream()
                .filter(t -> t.getVisibility() == Visibility.AVAILABLE && !visible.contains(t.getSectionKey()))
                .map(t -> new CatalogueSection(t.getSectionKey(), t.getName(), t.getKind()))
                .collect(Collectors.toList());
        SectionDefs.Lookup lookup = defs.lookup();
        List<CatalogueKpi> kpis = new ArrayList<>();
        for (MeetingSection s : m.getSections()) {
            if (s.isHidden() || s.getKind() != SectionKind.KPI) continue;
            Set<String> shown = lookup.kpis(m, s).stream().map(TemplateKpi::getKpiKey).collect(Collectors.toSet());
            latestDef(latest, s.getSectionKey()).ifPresent(t -> t.getKpis().stream()
                    .filter(k -> k.getVisibility() == Visibility.AVAILABLE && !shown.contains(k.getKpiKey()))
                    .forEach(k -> kpis.add(new CatalogueKpi(s.getSectionKey(), k.getKpiKey(), k.getName()))));
        }
        return new Catalogue(true, secs, kpis);
    }

    @Transactional
    public void sectionOn(long meetingId, String key, String user, boolean admin) {
        Meeting m = openForSwitching(meetingId, admin);
        TemplateVersion latest = templates.current();
        TemplateSection t = latestDef(latest, key).filter(d -> d.getVisibility() == Visibility.AVAILABLE)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_AVAILABLE", "That Section is not available to switch on."));
        Optional<MeetingSection> existing = m.getSections().stream().filter(s -> s.getSectionKey().equals(key)).findFirst();
        if (existing.isPresent() && !existing.get().isHidden()) {
            throw new AccountException(HttpStatus.CONFLICT, "ALREADY_ON", "That Section is already in this Meeting.");
        }
        MeetingSection s = existing.orElseGet(() -> {
            MeetingSection n = new MeetingSection(m, t.getSectionKey(), t.getName(), t.getTeamTag(), t.getKind(), t.getLayout(),
                    t.getOverflowRule(), t.isRequired(), t.getColumnsSpec(), t.getChartKpiKey(), 0);
            n.setOnSlides(t.isOnSlides());
            n.pinTo(latest.getVersionNo());
            n.markSwitchedOn();
            m.add(n);
            return n;
        });
        s.setHidden(false);
        reorder(m, latest);
        sections.saveAll(m.getSections());
        audit.record("SECTION_SWITCHED_ON", "Meeting", meetingId, key + " by " + user);
    }

    @Transactional
    public void sectionOff(long meetingId, String key, String user, boolean admin) {
        Meeting m = openForSwitching(meetingId, admin);
        MeetingSection s = visibleSection(m, key);
        if (!s.isSwitchedOn()) {
            throw new AccountException(HttpStatus.CONFLICT, "NOT_SWITCHABLE", "Only Sections switched on for this Meeting can be switched off.");
        }
        if (hasContent(read(s.getContentJson())) && !admin) {
            throw new AccountException(HttpStatus.FORBIDDEN, "HAS_CONTENT", "This Section has content. Ask an Admin to switch it off.");
        }
        s.setHidden(true); // content is kept, never deleted
        sections.save(s);
        audit.record("SECTION_SWITCHED_OFF", "Meeting", meetingId, key + " by " + user);
    }

    @Transactional
    public void kpiOn(long meetingId, String sectionKey, String kpiKey, String user, boolean admin) {
        Meeting m = openForSwitching(meetingId, admin);
        MeetingSection s = visibleSection(m, sectionKey);
        TemplateVersion latest = templates.current();
        latestDef(latest, sectionKey).flatMap(t -> t.getKpis().stream()
                        .filter(k -> k.getKpiKey().equals(kpiKey) && k.getVisibility() == Visibility.AVAILABLE).findFirst())
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_AVAILABLE", "That KPI is not available to switch on."));
        List<String> enabled = new ArrayList<>(s.getEnabledKpis());
        if (enabled.contains(kpiKey)) {
            throw new AccountException(HttpStatus.CONFLICT, "ALREADY_ON", "That KPI is already in this Section.");
        }
        enabled.add(kpiKey);
        s.pinTo(latest.getVersionNo()); // the KPI is defined in the latest version; ADR 0008
        s.setEnabledKpis(enabled);
        sections.save(s);
        audit.record("KPI_SWITCHED_ON", "Meeting", meetingId, sectionKey + "." + kpiKey + " by " + user);
    }

    @Transactional
    public void kpiOff(long meetingId, String sectionKey, String kpiKey, String user, boolean admin) {
        Meeting m = openForSwitching(meetingId, admin);
        MeetingSection s = visibleSection(m, sectionKey);
        List<String> enabled = new ArrayList<>(s.getEnabledKpis());
        if (!enabled.contains(kpiKey)) {
            throw new AccountException(HttpStatus.CONFLICT, "NOT_SWITCHABLE", "Only KPIs switched on for this Meeting can be switched off.");
        }
        JsonNode value = read(s.getContentJson()).path("values").path(kpiKey).path("value");
        if (!value.isMissingNode() && !value.isNull() && !admin) {
            throw new AccountException(HttpStatus.FORBIDDEN, "HAS_CONTENT", "This KPI has a value. Ask an Admin to switch it off.");
        }
        enabled.remove(kpiKey); // any value stays in the content, hidden
        s.setEnabledKpis(enabled);
        sections.save(s);
        audit.record("KPI_SWITCHED_OFF", "Meeting", meetingId, sectionKey + "." + kpiKey + " by " + user);
    }

    private Meeting openForSwitching(long meetingId, boolean admin) {
        Meeting m = meetings.live(meetingId);
        editor.requireEditable(m, admin);
        if (!preparing(m)) {
            throw new AccountException(HttpStatus.CONFLICT, "MEETING_HELD", "This Meeting has been held; its Sections and KPIs no longer change.");
        }
        return m;
    }

    private boolean preparing(Meeting m) {
        return "PREPARING".equals(time.label(m.getMeetingDate()));
    }

    private static MeetingSection visibleSection(Meeting m, String key) {
        return m.getSections().stream().filter(s -> s.getSectionKey().equals(key) && !s.isHidden()).findFirst()
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such Section in this Meeting."));
    }

    private static Optional<TemplateSection> latestDef(TemplateVersion latest, String key) {
        return latest.getSections().stream().filter(t -> t.getSectionKey().equals(key)).findFirst();
    }

    /** Sections follow the latest Template's order; Sections no longer in it keep their place relative to the others. */
    private static void reorder(Meeting m, TemplateVersion latest) {
        Map<String, Integer> pos = new HashMap<>();
        latest.getSections().forEach(t -> pos.put(t.getSectionKey(), pos.size()));
        List<MeetingSection> current = new ArrayList<>(m.getSections());
        Map<MeetingSection, Double> rank = new HashMap<>();
        double last = -1;
        for (MeetingSection s : current) {
            Integer p = pos.get(s.getSectionKey());
            last = p != null ? p : last + 0.001;
            rank.put(s, last);
        }
        current.sort(Comparator.comparingDouble(rank::get));
        for (int i = 0; i < current.size(); i++) {
            current.get(i).setSortOrder(i + 1);
        }
    }

    private JsonNode read(String content) {
        try {
            return content == null ? json.createObjectNode() : json.readTree(content);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    static boolean hasContent(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return false;
        if (n.isTextual()) return !n.asText().isBlank();
        if (n.isValueNode()) return true;
        for (JsonNode c : n) {
            if (hasContent(c)) return true;
        }
        return false;
    }
}
