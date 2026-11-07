package com.igamonmeet.meeting;

import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.template.TeamTag;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Action items (within the Edit window) and Comments (any time) for a Meeting (docs/spec.md §3.6–3.7). */
@Service
public class ActionsAndComments {

    private final MeetingService meetings;
    private final SectionEditor editor;
    private final ActionItemRepository actions;
    private final CommentRepository comments;
    private final MeetingTime time;
    private final AuditLog audit;

    public ActionsAndComments(MeetingService meetings, SectionEditor editor, ActionItemRepository actions, CommentRepository comments,
                              MeetingTime time, AuditLog audit) {
        this.meetings = meetings;
        this.editor = editor;
        this.actions = actions;
        this.comments = comments;
        this.time = time;
        this.audit = audit;
    }

    /** Overdue open items first, then other open items by due date, then closed items. */
    public List<ActionItem> ordered(long meetingId) {
        LocalDate today = time.today();
        Comparator<ActionItem> order = Comparator
                .comparingInt((ActionItem a) -> rank(a, today))
                .thenComparing(ActionItem::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(ActionItem::getSortOrder);
        return actions.findByMeetingIdOrderBySortOrder(meetingId).stream().sorted(order).collect(Collectors.toList());
    }

    public boolean overdue(ActionItem a) {
        return rank(a, time.today()) == 0;
    }

    private static int rank(ActionItem a, LocalDate today) {
        if (!ActionItem.OPEN.equals(a.getStatus())) {
            return 2;
        }
        return a.getDueDate() != null && a.getDueDate().isBefore(today) ? 0 : 1;
    }

    @Transactional
    public ActionItem add(long meetingId, String title, String owner, TeamTag team, LocalDate due, boolean admin) {
        Meeting m = meetings.live(meetingId);
        editor.requireEditable(m, admin);
        int next = actions.findByMeetingIdOrderBySortOrder(meetingId).stream().mapToInt(ActionItem::getSortOrder).max().orElse(0) + 1;
        ActionItem a = actions.save(new ActionItem(meetingId, title.trim(), owner.trim(), team, due, ActionItem.OPEN, null, next, time.now()));
        m.touch(time.now());
        audit.record("ACTION_ADDED", "Meeting", meetingId, a.getTitle());
        return a;
    }

    @Transactional
    public ActionItem update(long meetingId, long actionId, long version, String title, String owner, TeamTag team, LocalDate due,
                             String status, boolean admin) {
        Meeting m = meetings.live(meetingId);
        editor.requireEditable(m, admin);
        ActionItem a = item(meetingId, actionId);
        if (a.getVersion() != version) {
            throw new AccountException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Someone else changed this action item. Reload to see their change.");
        }
        if (!ActionItem.OPEN.equals(status) && !ActionItem.CLOSED.equals(status)) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID", "Status must be OPEN or CLOSED.");
        }
        String field = "action " + actionId;
        change(meetingId, field + ".title", a.getTitle(), title.trim());
        change(meetingId, field + ".owner", a.getOwner(), owner.trim());
        change(meetingId, field + ".team", a.getTeamTag(), team);
        change(meetingId, field + ".due", a.getDueDate(), due);
        change(meetingId, field + ".status", a.getStatus(), status);
        a.update(title.trim(), owner.trim(), team, due, status);
        m.touch(time.now());
        return actions.saveAndFlush(a);
    }

    @Transactional
    public void remove(long meetingId, long actionId, boolean admin) {
        Meeting m = meetings.live(meetingId);
        editor.requireEditable(m, admin);
        ActionItem a = item(meetingId, actionId);
        actions.delete(a);
        m.touch(time.now());
        audit.record("ACTION_REMOVED", "Meeting", meetingId, a.getTitle());
    }

    @Transactional
    public Comment comment(long meetingId, String sectionKey, Long parentId, String body, String user) {
        Meeting m = meetings.live(meetingId);
        boolean sectionExists = m.getSections().stream().anyMatch(s -> s.getSectionKey().equals(sectionKey));
        if (!sectionExists) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID", "Unknown section.");
        }
        if (parentId != null) {
            Comment parent = comments.findById(parentId).filter(p -> p.getMeetingId() == meetingId)
                    .orElseThrow(() -> new AccountException(HttpStatus.BAD_REQUEST, "INVALID", "Unknown comment."));
            if (parent.getParentId() != null || !parent.getSectionKey().equals(sectionKey)) {
                throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID", "Reply to the first comment of a thread.");
            }
        }
        Comment c = comments.save(new Comment(meetingId, sectionKey, parentId, user, body.trim(), time.now()));
        audit.record("COMMENT_ADDED", "Meeting", meetingId, sectionKey);
        return c;
    }

    @Transactional
    public void resolve(long meetingId, long commentId, boolean resolved, String user) {
        meetings.live(meetingId);
        Comment c = comments.findById(commentId).filter(x -> x.getMeetingId() == meetingId && x.getParentId() == null)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Thread not found."));
        c.resolve(resolved, user, time.now());
        audit.record(resolved ? "COMMENT_RESOLVED" : "COMMENT_REOPENED", "Meeting", meetingId, c.getSectionKey());
    }

    private ActionItem item(long meetingId, long actionId) {
        return actions.findById(actionId).filter(a -> a.getMeetingId() == meetingId)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Action item not found."));
    }

    private void change(long meetingId, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            audit.recordChange("Meeting", meetingId, field, before, after);
        }
    }
}
