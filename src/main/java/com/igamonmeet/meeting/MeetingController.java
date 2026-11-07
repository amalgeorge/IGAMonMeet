package com.igamonmeet.meeting;

import com.igamonmeet.template.OverflowRule;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetingController {

    public record CreateRequest(@NotNull YearMonth month, @NotNull LocalDate meetingDate, @Size(max = 200) String title) {
    }

    public record Row(long id, String month, String title, LocalDate meetingDate, String label, Instant editableUntil,
                      boolean editWindowOpen, int templateVersionNo, long openActions, Instant updatedAt) {
    }

    public record DeletedRow(long id, String month, String title, Instant deletedAt, String deletedBy, Instant purgeAfter) {
    }

    public record SectionView(long id, String key, String name, TeamTag teamTag, SectionKind kind, String layout, OverflowRule overflowRule,
                              boolean required, List<String> columns, String chartKpiKey, String contentJson, String updatedBy,
                              Instant updatedAt, long version) {
    }

    public record ActionView(long id, String title, String owner, TeamTag teamTag, LocalDate dueDate, String status, Long originMeetingId) {
    }

    public record Detail(Row meeting, List<SectionView> sections, List<ActionView> actionItems) {
    }

    private final MeetingService service;
    private final MeetingRepository meetings;
    private final ActionItemRepository actions;
    private final MeetingTime time;

    public MeetingController(MeetingService service, MeetingRepository meetings, ActionItemRepository actions, MeetingTime time) {
        this.service = service;
        this.meetings = meetings;
        this.actions = actions;
        this.time = time;
    }

    @GetMapping("/api/meetings")
    @Transactional(readOnly = true)
    public List<Row> list(@RequestParam(required = false) String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return meetings.findByDeletedAtIsNullOrderByMeetingDateDesc().stream()
                .filter(m -> needle.isEmpty() || m.getTitle().toLowerCase(Locale.ROOT).contains(needle) || m.getMonth().contains(needle))
                .map(this::row)
                .collect(Collectors.toList());
    }

    @GetMapping("/api/meetings/{id}")
    @Transactional(readOnly = true)
    public Detail get(@PathVariable long id) {
        Meeting m = service.live(id);
        List<SectionView> sections = m.getSections().stream().map(s -> new SectionView(s.getId(), s.getSectionKey(), s.getName(), s.getTeamTag(),
                s.getKind(), s.getLayout(), s.getOverflowRule(), s.isRequired(),
                s.getColumnsSpec() == null ? List.of() : Arrays.asList(s.getColumnsSpec().split("\\|")),
                s.getChartKpiKey(), s.getContentJson(), s.getUpdatedBy(), s.getUpdatedAt(), s.getVersion())).collect(Collectors.toList());
        List<ActionView> items = actions.findByMeetingIdOrderBySortOrder(id).stream()
                .map(a -> new ActionView(a.getId(), a.getTitle(), a.getOwner(), a.getTeamTag(), a.getDueDate(), a.getStatus(), a.getOriginMeetingId()))
                .collect(Collectors.toList());
        return new Detail(row(m), sections, items);
    }

    @PostMapping("/api/admin/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Row create(@Valid @RequestBody CreateRequest r, Authentication auth) {
        return row(service.create(r.month(), r.meetingDate(), r.title(), auth.getName()));
    }

    @DeleteMapping("/api/admin/meetings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication auth) {
        service.delete(id, auth.getName());
    }

    @GetMapping("/api/admin/meetings/deleted")
    @Transactional(readOnly = true)
    public List<DeletedRow> deleted() {
        return meetings.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                .map(m -> new DeletedRow(m.getId(), m.getMonth(), m.getTitle(), m.getDeletedAt(), m.getDeletedBy(),
                        m.getDeletedAt().plus(MeetingService.RESTORE_PERIOD)))
                .collect(Collectors.toList());
    }

    @PostMapping("/api/admin/meetings/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable long id) {
        service.restore(id);
    }

    private Row row(Meeting m) {
        return new Row(m.getId(), m.getMonth(), m.getTitle(), m.getMeetingDate(), time.label(m.getMeetingDate()),
                time.editWindowCloses(m.getMeetingDate()), time.editWindowOpen(m.getMeetingDate()), m.getTemplateVersionNo(),
                actions.countByMeetingIdAndStatus(m.getId(), ActionItem.OPEN), m.getUpdatedAt());
    }
}
