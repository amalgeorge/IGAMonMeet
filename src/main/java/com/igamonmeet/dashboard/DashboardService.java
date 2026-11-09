package com.igamonmeet.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.meeting.CommentRepository;
import com.igamonmeet.meeting.KpiView;
import com.igamonmeet.meeting.Meeting;
import com.igamonmeet.meeting.MeetingController.ActionView;
import com.igamonmeet.meeting.MeetingController.Detail;
import com.igamonmeet.meeting.MeetingController.SectionView;
import com.igamonmeet.meeting.MeetingDetails;
import com.igamonmeet.meeting.MeetingRepository;
import com.igamonmeet.meeting.MeetingSection;
import com.igamonmeet.template.SectionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Review dashboard for one Meeting (docs/spec.md §4.3). Read-only; built from the same data as the Document view. */
@Service
public class DashboardService {

    static final int TREND_MONTHS = 6;
    private static final DateTimeFormatter SHORT_MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    public record Trend(List<String> labels, List<BigDecimal> values) {
    }

    public record KpiTile(KpiView kpi, String section, String group, Trend trend) {
    }

    public record Health(String sectionKey, String section, Map<String, Integer> counts, int total) {
    }

    public record CommentRow(String section, String author, String body, Instant createdAt, int replies) {
    }

    public record Dashboard(String title, List<KpiTile> kpis, List<Health> health, int openActions, int overdueActions,
                            List<ActionView> actions, int openComments, List<CommentRow> comments, int requiredSections, int requiredFilled) {
    }

    private final MeetingDetails details;
    private final MeetingRepository meetings;
    private final CommentRepository comments;
    private final ObjectMapper json;

    public DashboardService(MeetingDetails details, MeetingRepository meetings, CommentRepository comments, ObjectMapper json) {
        this.details = details;
        this.meetings = meetings;
        this.comments = comments;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Dashboard build(long meetingId) {
        Detail d = details.detail(meetingId, false);
        Meeting m = meetings.findById(meetingId).orElseThrow();
        Map<String, String> names = new LinkedHashMap<>();
        d.sections().forEach(s -> names.put(s.key(), s.name()));

        // Up to six Meetings up to and including this one, oldest first.
        List<Meeting> history = new ArrayList<>(meetings.findTop12ByDeletedAtIsNullAndMeetingDateLessThanEqualOrderByMeetingDateDesc(m.getMeetingDate()));
        history = history.subList(0, Math.min(TREND_MONTHS, history.size()));
        Collections.reverse(history);

        List<KpiTile> tiles = new ArrayList<>();
        List<Health> health = new ArrayList<>();
        for (SectionView s : d.sections()) {
            if (s.kind() == SectionKind.KPI) {
                for (KpiView k : s.kpis()) {
                    tiles.add(new KpiTile(k, s.name(), s.groupName(), trend(history, s.key(), k.key())));
                }
            }
            if (s.kind() == SectionKind.TABLE) {
                int col = indexOfIgnoreCase(s.columns(), "Status");
                if (col >= 0) {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    int total = 0;
                    for (JsonNode row : read(s.contentJson()).path("rows")) {
                        String status = row.path(col).asText("").trim();
                        counts.merge(status.isEmpty() ? "No status" : status, 1, Integer::sum);
                        total++;
                    }
                    health.add(new Health(s.key(), s.name(), counts, total));
                }
            }
        }

        List<ActionView> open = d.actionItems().stream().filter(a -> "OPEN".equals(a.status())).toList();
        int overdue = (int) open.stream().filter(ActionView::overdue).count();

        List<CommentRow> openThreads = new ArrayList<>();
        var all = comments.findByMeetingIdOrderByCreatedAtAscIdAsc(meetingId);
        all.stream().filter(c -> c.getParentId() == null && !c.isResolved()).forEach(c -> openThreads.add(new CommentRow(
                names.getOrDefault(c.getSectionKey(), c.getSectionKey()), c.getAuthor(), c.getBody(), c.getCreatedAt(),
                (int) all.stream().filter(x -> c.getId().equals(x.getParentId())).count())));
        Collections.reverse(openThreads);

        return new Dashboard(d.meeting().title(), tiles, health, open.size(), overdue, open, openThreads.size(), openThreads,
                d.requiredSections(), d.requiredFilled());
    }

    private Trend trend(List<Meeting> history, String sectionKey, String kpiKey) {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (Meeting past : history) {
            // A KPI keeps one history across Sections (spec-v2 §2.1): this Section's value first, else any Section's.
            BigDecimal v = null;
            for (MeetingSection s : past.getSections()) {
                JsonNode n = read(s.getContentJson()).path("values").path(kpiKey).path("value");
                if (n.isNumber() && (v == null || s.getSectionKey().equals(sectionKey))) {
                    v = n.decimalValue();
                }
            }
            labels.add(SHORT_MONTH.format(YearMonth.parse(past.getMonth())));
            values.add(v);
        }
        return new Trend(labels, values);
    }

    private static int indexOfIgnoreCase(List<String> list, String v) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).trim().equalsIgnoreCase(v)) return i;
        }
        return -1;
    }

    private JsonNode read(String s) {
        try {
            return s == null ? json.createObjectNode() : json.readTree(s);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }
}
