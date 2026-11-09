package com.igamonmeet.slides;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.house.HouseTemplateService;
import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.meeting.Meeting;
import com.igamonmeet.meeting.MeetingController.ActionView;
import com.igamonmeet.meeting.MeetingController.Detail;
import com.igamonmeet.meeting.MeetingController.SectionView;
import com.igamonmeet.meeting.MeetingDetails;
import com.igamonmeet.meeting.MeetingRepository;
import com.igamonmeet.meeting.MeetingSection;
import com.igamonmeet.slides.PlanModel.ActionRow;
import com.igamonmeet.slides.PlanModel.Box;
import com.igamonmeet.slides.PlanModel.Input;
import com.igamonmeet.slides.PlanModel.Plan;
import com.igamonmeet.slides.PlanModel.SectionInput;
import com.igamonmeet.slides.PlanModel.Series;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gathers a Meeting's data and the House template theme, then asks the planner for the Slide plan. */
@Service
public class SlidePlanService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final MeetingDetails details;
    private final MeetingRepository meetings;
    private final HouseTemplateService house;
    private final SlidePlanner planner;
    private final ObjectMapper json;

    public SlidePlanService(MeetingDetails details, MeetingRepository meetings, HouseTemplateService house, SlidePlanner planner, ObjectMapper json) {
        this.details = details;
        this.meetings = meetings;
        this.house = house;
        this.planner = planner;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Plan plan(long meetingId) {
        return plan(meetingId, null);
    }

    /** Plan with only the given Sections (null = all), so dividers and numbering still fit the export. */
    @Transactional(readOnly = true)
    public Plan plan(long meetingId, java.util.Set<String> include) {
        Detail d = details.detail(meetingId, true);
        Meeting m = meetings.findById(meetingId).orElseThrow();
        JsonNode theme = read(house.currentTheme());
        double w = theme.path("slideWidth").asDouble(960);
        double h = theme.path("slideHeight").asDouble(540);
        Map<String, Box> boxes = new HashMap<>();
        box(theme, "IAM_SUMMARY", "body", w, h, boxes);
        box(theme, "IAM_TWO_COLUMN", "left", w, h, boxes);

        Map<String, KpiView> kpiDefs = new HashMap<>();
        d.sections().forEach(s -> s.kpis().forEach(k -> kpiDefs.put(k.key(), k)));

        List<SectionInput> sections = new ArrayList<>();
        for (SectionView s : d.sections()) {
            if (!s.onSlides() || (include != null && !include.contains(s.key()))) {
                continue; // Admin keeps some Sections in the Document and Dashboard views only (spec-v2 §1.2).
            }
            Series series = s.kind() == SectionKind.CHART && s.chartKpiKey() != null ? series(m, s.chartKpiKey(), kpiDefs.get(s.chartKpiKey())) : null;
            sections.add(new SectionInput(s.key(), s.name(), s.teamTag(), s.kind(), s.layout(), s.overflowRule(), s.required(),
                    s.filled(), s.columns(), read(s.contentJson()), s.kpis(), series,
                    s.groupKey() == null ? null : new PlanModel.SectionGroup(s.groupKey(), s.groupName(), s.groupDivider()), s.kpiLayout(),
                    s.tableStyle() == null ? null : readStyle(s.tableStyle())));
        }
        List<ActionRow> actions = new ArrayList<>();
        for (ActionView a : d.actionItems()) {
            actions.add(new ActionRow(a.title(), a.owner(), team(a.teamTag()),
                    a.dueDate() == null ? "—" : SHORT_DATE.format(a.dueDate()) + (a.overdue() ? " · overdue" : ""),
                    a.overdue(), "CLOSED".equals(a.status())));
        }
        Input in = new Input(m.getTitle(), MONTH.format(YearMonth.parse(m.getMonth())), LONG_DATE.format(m.getMeetingDate()),
                sections, actions, w, h, boxes);
        return planner.plan(in);
    }

    /** The KPI's value in this and up to 11 earlier Meetings, oldest first; the planner keeps the most recent. */
    private Series series(Meeting m, String kpiKey, KpiView def) {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Meeting past : meetings.findTop12ByDeletedAtIsNullAndMeetingDateLessThanEqualOrderByMeetingDateDesc(m.getMeetingDate())) {
            for (MeetingSection s : past.getSections()) {
                JsonNode v = read(s.getContentJson()).path("values").path(kpiKey).path("value");
                if (s.getKind() == SectionKind.KPI && v.isNumber()) {
                    labels.add(SHORT_MONTH.format(YearMonth.parse(past.getMonth())));
                    values.add(v.decimalValue());
                    break; // one point per Meeting, even when the KPI is placed in several Sections
                }
            }
        }
        Collections.reverse(labels);
        Collections.reverse(values);
        return new Series(labels, values, def == null ? null : def.target(), def == null ? "" : def.unit());
    }

    private JsonNode readStyle(String s) {
        try {
            return json.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static void box(JsonNode theme, String layout, String placeholder, double w, double h, Map<String, Box> out) {
        JsonNode b = theme.path("layouts").path(layout).path("placeholders").path(placeholder);
        if (b.has("w") && b.has("h")) {
            out.put(layout, new Box(b.get("w").asDouble() / 100 * w, b.get("h").asDouble() / 100 * h));
        }
    }

    private static String team(TeamTag t) {
        return t == TeamTag.ENGINEERING ? "Engineering" : t == TeamTag.OPERATIONS ? "Operations" : "Shared";
    }

    private JsonNode read(String s) {
        try {
            return s == null ? json.createObjectNode() : json.readTree(s);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }
}
