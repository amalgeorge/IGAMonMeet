package com.igamonmeet.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.meeting.ActionItem;
import com.igamonmeet.meeting.ActionItemRepository;
import com.igamonmeet.meeting.Meeting;
import com.igamonmeet.meeting.MeetingController.SectionView;
import com.igamonmeet.meeting.MeetingDetails;
import com.igamonmeet.meeting.MeetingRepository;
import com.igamonmeet.meeting.MeetingSection;
import com.igamonmeet.meeting.MeetingService;
import com.igamonmeet.meeting.MeetingTime;
import com.igamonmeet.meeting.SectionEditor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Named Versions, field-level diff and restore (docs/spec.md §7.1). */
@Service
public class VersionService {

    public record Change(String section, String field, String before, String after) {
    }

    public record Skipped(String section, String reason) {
    }

    public record RestoreResult(int beforeVersionNo, List<String> restored, List<Skipped> skipped, String note) {
    }

    private final MeetingVersionRepository versions;
    private final MeetingRepository meetingRepo;
    private final MeetingService meetings;
    private final MeetingDetails details;
    private final SectionEditor editor;
    private final ActionItemRepository actions;
    private final MeetingTime time;
    private final AuditLog audit;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public VersionService(MeetingVersionRepository versions, MeetingRepository meetingRepo, MeetingService meetings, MeetingDetails details,
                          SectionEditor editor, ActionItemRepository actions, MeetingTime time, AuditLog audit, ObjectMapper json,
                          PlatformTransactionManager txManager) {
        this.tx = new TransactionTemplate(txManager);
        this.versions = versions;
        this.meetingRepo = meetingRepo;
        this.meetings = meetings;
        this.details = details;
        this.editor = editor;
        this.actions = actions;
        this.time = time;
        this.audit = audit;
        this.json = json;
    }

    /** Stores the Meeting as it is now. */
    @Transactional
    public MeetingVersion snapshot(long meetingId, String reason, String label, String user) {
        Meeting m = meetings.live(meetingId);
        ObjectNode root = json.createObjectNode();
        ObjectNode secs = root.putObject("sections");
        for (MeetingSection s : m.getSections()) {
            ObjectNode n = secs.putObject(s.getSectionKey());
            n.put("name", s.getName());
            n.set("content", read(s.getContentJson()));
        }
        ArrayNode acts = root.putArray("actions");
        for (ActionItem a : actions.findByMeetingIdOrderBySortOrder(meetingId)) {
            acts.addObject().put("title", a.getTitle()).put("owner", a.getOwner()).put("team", a.getTeamTag().name())
                    .put("due", a.getDueDate() == null ? null : a.getDueDate().toString()).put("status", a.getStatus());
        }
        MeetingVersion v = versions.save(new MeetingVersion(meetingId, versions.latest(meetingId) + 1, reason,
                label.length() > 200 ? label.substring(0, 200) : label, root.toString(), user, time.now()));
        audit.recordAs(user, "VERSION_SAVED", "Meeting", meetingId, "v" + v.getVersionNo() + " " + reason + ": " + v.getLabel());
        return v;
    }

    @Transactional
    public MeetingVersion saveManual(long meetingId, String label, String user, boolean admin) {
        editor.requireEditable(meetings.live(meetingId), admin);
        return snapshot(meetingId, MeetingVersion.MANUAL, label == null || label.isBlank() ? "Saved by hand" : label.trim(), user);
    }

    public List<MeetingVersion> list(long meetingId) {
        meetings.live(meetingId);
        return versions.findByMeetingIdOrderByVersionNoDesc(meetingId);
    }

    /** Field-level differences between two Versions; {@code to} null means the current state. */
    @Transactional(readOnly = true)
    public List<Change> diff(long meetingId, int from, Integer to) {
        JsonNode a = state(meetingId, from);
        JsonNode b = to == null ? current(meetingId) : state(meetingId, to);
        List<Change> out = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        a.path("sections").fieldNames().forEachRemaining(keys::add);
        b.path("sections").fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            JsonNode ca = a.path("sections").path(key).path("content");
            JsonNode cb = b.path("sections").path(key).path("content");
            String name = b.path("sections").path(key).path("name").asText(a.path("sections").path(key).path("name").asText(key));
            Set<String> fields = new LinkedHashSet<>();
            for (Iterator<String> it = ca.fieldNames(); it.hasNext(); ) fields.add(it.next());
            for (Iterator<String> it = cb.fieldNames(); it.hasNext(); ) fields.add(it.next());
            for (String f : fields) {
                JsonNode x = ca.get(f);
                JsonNode y = cb.get(f);
                if (!Objects.equals(x, y)) {
                    out.add(new Change(name, f, text(x), text(y)));
                }
            }
        }
        Set<String> before = actionKeys(a);
        Set<String> after = actionKeys(b);
        before.stream().filter(k -> !after.contains(k)).forEach(k -> out.add(new Change("Action items", "item", k, null)));
        after.stream().filter(k -> !before.contains(k)).forEach(k -> out.add(new Change("Action items", "item", null, k)));
        return out;
    }

    /** Puts Section content back as it was in a Version. Saves the current state first so the restore can be undone. */
    @Transactional
    public RestoreResult restore(long meetingId, int versionNo, String user, boolean admin) {
        editor.requireEditable(meetings.live(meetingId), admin);
        JsonNode target = state(meetingId, versionNo);
        MeetingVersion before = snapshot(meetingId, MeetingVersion.RESTORE, "Before restoring v" + versionNo, user);
        List<String> restored = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (SectionView s : details.detail(meetingId, admin).sections()) {
            JsonNode want = target.path("sections").path(s.key()).path("content");
            JsonNode have = read(s.contentJson());
            if (want.isMissingNode() || want.equals(have) || want.isNull() || (want.isObject() && want.size() == 0 && s.contentJson() == null)) {
                continue;
            }
            try {
                editor.save(meetingId, s.id(), s.version(), want, user, admin);
                restored.add(s.key());
            } catch (AccountException e) {
                skipped.add(new Skipped(s.key(), e.getMessage()));
            }
        }
        audit.record("VERSION_RESTORED", "Meeting", meetingId, "Restored v" + versionNo + " (" + restored.size() + " sections)");
        return new RestoreResult(before.getVersionNo(), restored, skipped, "Action items are not changed by a restore.");
    }

    /** 23:59 Singapore time: keep what each Meeting held today looked like ("as presented"). */
    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Singapore")
    public void snapshotToday() {
        // Scheduled calls bypass the proxy, so the transaction is opened here.
        tx.executeWithoutResult(t -> snapshotHeldOn(time.today(), "As presented"));
    }

    /** After a restart, catches Meetings of the last two days that missed their snapshot. */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Singapore")
    public void catchUp() {
        LocalDate today = time.today();
        tx.executeWithoutResult(t -> {
            snapshotHeldOn(today.minusDays(1), "As presented (taken late)");
            snapshotHeldOn(today.minusDays(2), "As presented (taken late)");
        });
    }

    @Transactional
    public int snapshotHeldOn(LocalDate date, String label) {
        int n = 0;
        for (Meeting m : meetingRepo.findByDeletedAtIsNullOrderByMeetingDateDesc()) {
            if (m.getMeetingDate().equals(date) && !versions.existsByMeetingIdAndReason(m.getId(), MeetingVersion.SNAPSHOT)) {
                snapshot(m.getId(), MeetingVersion.SNAPSHOT, label, "system");
                n++;
            }
        }
        return n;
    }

    private JsonNode state(long meetingId, int versionNo) {
        return versions.findByMeetingIdAndVersionNo(meetingId, versionNo).map(v -> read(v.getSnapshotJson()))
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No version " + versionNo + "."));
    }

    private JsonNode current(long meetingId) {
        Meeting m = meetings.live(meetingId);
        ObjectNode root = json.createObjectNode();
        ObjectNode secs = root.putObject("sections");
        m.getSections().forEach(s -> secs.putObject(s.getSectionKey()).put("name", s.getName()).set("content", read(s.getContentJson())));
        ArrayNode acts = root.putArray("actions");
        actions.findByMeetingIdOrderBySortOrder(meetingId).forEach(a -> acts.addObject().put("title", a.getTitle()).put("owner", a.getOwner())
                .put("status", a.getStatus()));
        return root;
    }

    private static Set<String> actionKeys(JsonNode state) {
        Set<String> out = new LinkedHashSet<>();
        state.path("actions").forEach(a -> out.add(a.path("title").asText() + " · " + a.path("owner").asText() + " · " + a.path("status").asText()));
        return out;
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull()) return null;
        return n.isTextual() ? n.asText() : n.toString();
    }

    private JsonNode read(String s) {
        try {
            return s == null ? json.createObjectNode() : json.readTree(s);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }
}
