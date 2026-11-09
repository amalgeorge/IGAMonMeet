package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.meeting.MeetingController.ActionView;
import com.igamonmeet.meeting.MeetingController.Detail;
import com.igamonmeet.meeting.MeetingController.Row;
import com.igamonmeet.meeting.MeetingController.SectionView;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TemplateKpi;
import com.igamonmeet.template.TemplateSection;
import com.igamonmeet.template.TemplateService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the full read model of a Meeting, used by the API and by the slide engine. */
@Service
public class MeetingDetails {

    private final MeetingService service;
    private final MeetingRepository meetings;
    private final ActionItemRepository actions;
    private final MeetingTime time;
    private final SectionLockRepository locks;
    private final ObjectMapper json;
    private final TemplateService templates;
    private final ActionsAndComments actionsAndComments;
    private final SectionDefs sectionDefs;
    private final com.igamonmeet.settings.Settings settings;

    public MeetingDetails(MeetingService service, MeetingRepository meetings, ActionItemRepository actions, MeetingTime time,
                          SectionLockRepository locks, ObjectMapper json, TemplateService templates, ActionsAndComments actionsAndComments,
                          SectionDefs sectionDefs, com.igamonmeet.settings.Settings settings) {
        this.settings = settings;
        this.sectionDefs = sectionDefs;
        this.service = service;
        this.meetings = meetings;
        this.actions = actions;
        this.time = time;
        this.locks = locks;
        this.json = json;
        this.templates = templates;
        this.actionsAndComments = actionsAndComments;
    }

    @Transactional(readOnly = true)
    public Detail detail(long id, boolean admin) {
        Meeting m = service.live(id);
        Instant now = time.now();
        long openItems = actions.findByMeetingIdOrderBySortOrder(id).size();
        SectionDefs.Lookup defs = sectionDefs.lookup();
        java.util.Map<String, String> labels = settings.statusLabels();
        Meeting previous = meetings.findFirstByDeletedAtIsNullAndMeetingDateLessThanOrderByMeetingDateDesc(m.getMeetingDate()).orElse(null);
        List<SectionView> sections = m.getSections().stream().filter(s -> !s.isHidden()).map(s -> {
            List<TemplateKpi> kpiDefs = defs.kpis(m, s);
            Set<String> requiredKpis = kpiDefs.stream().filter(TemplateKpi::isRequired).map(TemplateKpi::getKpiKey).collect(Collectors.toSet());
            List<KpiView> kpis = List.of();
            if (s.getKind() == SectionKind.KPI) {
                JsonNode values = read(s.getContentJson()).path("values");
                JsonNode prevValues = previous == null ? null : previousValues(previous, s.getSectionKey());
                kpis = kpiDefs.stream().map(k -> KpiView.of(k, values, prevValues, labels)).collect(Collectors.toList());
            }
            return new SectionView(s.getId(), s.getSectionKey(), s.getName(), s.getTeamTag(),
                    s.getKind(), s.getLayout(), s.getOverflowRule(), s.isRequired(),
                    s.getColumnsSpec() == null ? List.of() : Arrays.asList(s.getColumnsSpec().split("\\|")),
                    s.getChartKpiKey(), s.getContentJson(), s.getUpdatedBy(), s.getUpdatedAt(), s.getVersion(),
                    locks.findById(s.getId()).filter(l -> l.isLiveAt(now)).map(SectionLock::getUsername).orElse(null),
                    SectionContent.filled(s.getKind(), s.getContentJson(), openItems, requiredKpis, json), kpis, s.isOnSlides(),
                    s.isSwitchedOn(), s.getEnabledKpis(),
                    defs.group(m, s).map(g -> g.getGroupKey()).orElse(null), defs.group(m, s).map(g -> g.getName()).orElse(null),
                    defs.group(m, s).map(g -> g.isDivider()).orElse(false),
                    defs.def(m, s).map(d -> d.getKpiLayout()).orElse(null),
                    defs.def(m, s).map(d -> d.getTableStyle()).orElse(null),
                    defs.def(m, s).map(d -> d.getSlideLayout()).orElse(null));
        }).collect(Collectors.toList());
        List<ActionView> items = actionsAndComments.ordered(id).stream()
                .map(a -> new ActionView(a.getId(), a.getTitle(), a.getOwner(), a.getTeamTag(), a.getDueDate(), a.getStatus(), a.getOriginMeetingId(),
                        actionsAndComments.overdue(a), a.getVersion()))
                .collect(Collectors.toList());
        int required = (int) sections.stream().filter(SectionView::required).count();
        int filled = (int) sections.stream().filter(v -> v.required() && v.filled()).count();
        boolean canEdit = admin || time.editWindowOpen(m.getMeetingDate());
        return new Detail(row(m), canEdit, required, filled, sections, items);
    }

    /** Last month's KPI values from every KPI Section, this Section's winning, so a moved KPI keeps its history (spec-v2 §2.1). */
    private JsonNode previousValues(Meeting previous, String sectionKey) {
        com.fasterxml.jackson.databind.node.ObjectNode merged = json.createObjectNode();
        previous.getSections().stream().filter(p -> p.getKind() == SectionKind.KPI && !p.getSectionKey().equals(sectionKey))
                .forEach(p -> read(p.getContentJson()).path("values").fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue())));
        previous.getSections().stream().filter(p -> p.getSectionKey().equals(sectionKey))
                .forEach(p -> read(p.getContentJson()).path("values").fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue())));
        return merged;
    }

        private JsonNode read(String content) {
        try {
            return content == null ? json.createObjectNode() : json.readTree(content);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    public Row row(Meeting m) {
        return new Row(m.getId(), m.getMonth(), m.getTitle(), m.getMeetingDate(), time.label(m.getMeetingDate()),
                time.editWindowCloses(m.getMeetingDate()), time.editWindowOpen(m.getMeetingDate()), m.getTemplateVersionNo(),
                actions.countByMeetingIdAndStatus(m.getId(), ActionItem.OPEN), m.getUpdatedAt());
    }
}
