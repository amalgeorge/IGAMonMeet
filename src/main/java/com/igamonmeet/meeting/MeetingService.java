package com.igamonmeet.meeting;

import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.template.TemplateSection;
import com.igamonmeet.template.TemplateService;
import com.igamonmeet.template.TemplateVersion;
import com.igamonmeet.template.Visibility;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

    public static final Duration RESTORE_PERIOD = Duration.ofDays(30);
    private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final MeetingRepository meetings;
    private final ActionItemRepository actions;
    private final TemplateService templates;
    private final AuditLog audit;
    private final MeetingTime time;

    public MeetingService(MeetingRepository meetings, ActionItemRepository actions, TemplateService templates, AuditLog audit, MeetingTime time) {
        this.meetings = meetings;
        this.actions = actions;
        this.templates = templates;
        this.audit = audit;
        this.time = time;
    }

    /** Admin only. Copies the current Template's Sections and carries forward the previous Meeting's open Action items. */
    @Transactional
    public Meeting create(YearMonth month, LocalDate meetingDate, String title, String admin) {
        String key = month.toString();
        if (meetings.existsByMonthAndDeletedAtIsNull(key)) {
            throw new AccountException(HttpStatus.CONFLICT, "MONTH_EXISTS", "A meeting for " + MONTH_NAME.format(month) + " already exists.");
        }
        Instant now = time.now();
        TemplateVersion template = templates.current();
        String name = title == null || title.isBlank() ? "IAM Monthly Meeting · " + MONTH_NAME.format(month) : title.trim();
        Meeting m = new Meeting(key, meetingDate, name, template.getVersionNo(), admin, now);
        int order = 0;
        for (TemplateSection s : template.getSections()) {
            if (template.effectiveVisibility(s) != Visibility.SHOWN) {
                continue; // Available ones are switched on per Meeting (ticket 20); Retired ones never appear.
            }
            MeetingSection ms = new MeetingSection(m, s.getSectionKey(), s.getName(), s.getTeamTag(), s.getKind(), s.getLayout(),
                    s.getOverflowRule(), s.isRequired(), s.getColumnsSpec(), s.getChartKpiKey(), ++order);
            ms.setOnSlides(s.isOnSlides());
            m.add(ms);
        }
        meetings.save(m);

        int carried = 0;
        var previous = meetings.findFirstByDeletedAtIsNullAndMeetingDateLessThanOrderByMeetingDateDesc(meetingDate);
        if (previous.isPresent()) {
            for (ActionItem a : actions.findByMeetingIdAndStatusOrderBySortOrder(previous.get().getId(), ActionItem.OPEN)) {
                Long origin = a.getOriginMeetingId() != null ? a.getOriginMeetingId() : a.getMeetingId();
                actions.save(new ActionItem(m.getId(), a.getTitle(), a.getOwner(), a.getTeamTag(), a.getDueDate(), ActionItem.OPEN,
                        origin, ++carried, now));
            }
        }
        audit.record("MEETING_CREATED", "Meeting", m.getId(), name + " (template v" + template.getVersionNo() + ", " + carried + " actions carried)");
        return m;
    }

    @Transactional
    public void delete(long id, String admin) {
        Meeting m = live(id);
        m.softDelete(admin, time.now());
        audit.record("MEETING_DELETED", "Meeting", id, m.getTitle());
    }

    @Transactional
    public void restore(long id) {
        Meeting m = meetings.findById(id).filter(x -> x.getDeletedAt() != null)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No deleted meeting with that id."));
        if (meetings.existsByMonthAndDeletedAtIsNull(m.getMonth())) {
            throw new AccountException(HttpStatus.CONFLICT, "MONTH_EXISTS", "Another meeting now exists for that month. Delete it first.");
        }
        m.restore();
        audit.record("MEETING_RESTORED", "Meeting", id, m.getTitle());
    }

    /** Daily: permanently removes Meetings deleted more than 30 days ago. Audit rows stay. */
    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Singapore")
    @Transactional
    public int purge() {
        List<Meeting> expired = meetings.findByDeletedAtBefore(time.now().minus(RESTORE_PERIOD));
        for (Meeting m : expired) {
            actions.deleteByMeeting(m.getId());
            meetings.delete(m);
            audit.recordAs("system", "MEETING_PURGED", "Meeting", m.getId(), m.getTitle());
        }
        return expired.size();
    }

    @Transactional(readOnly = true)
    public Meeting live(long id) {
        return meetings.findById(id).filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Meeting not found."));
    }
}
