package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.template.TemplateService;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Editing Sections: Edit window, Section locks and the version check (docs/spec.md §3.2–3.4). */
@Service
public class SectionEditor {

    public static final Duration LOCK_IDLE = Duration.ofMinutes(15);

    private final MeetingService meetings;
    private final MeetingSectionRepository sections;
    private final SectionLockRepository locks;
    private final MeetingTime time;
    private final AuditLog audit;
    private final ObjectMapper json;
    private final TemplateService templates;

    public SectionEditor(MeetingService meetings, MeetingSectionRepository sections, SectionLockRepository locks,
                         MeetingTime time, AuditLog audit, ObjectMapper json, TemplateService templates) {
        this.templates = templates;
        this.meetings = meetings;
        this.sections = sections;
        this.locks = locks;
        this.time = time;
        this.audit = audit;
        this.json = json;
    }

    /** End users may edit only inside the Edit window; Admins always. */
    public void requireEditable(Meeting m, boolean admin) {
        if (!admin && !time.editWindowOpen(m.getMeetingDate())) {
            throw new AccountException(HttpStatus.FORBIDDEN, "EDIT_WINDOW_CLOSED",
                    "This meeting can no longer be edited. Ask an Admin to make changes.");
        }
    }

    @Transactional
    public void lock(long meetingId, long sectionId, String user, boolean admin) {
        MeetingSection s = section(meetingId, sectionId);
        requireEditable(s.getMeeting(), admin);
        Instant now = time.now();
        SectionLock lock = locks.findById(sectionId).orElse(null);
        if (lock == null) {
            locks.save(new SectionLock(sectionId, user, now));
        } else if (lock.isHeldBy(user) || !lock.isLiveAt(now)) {
            lock.takeOver(user, now);
        } else {
            throw lockedBy(lock);
        }
    }

    @Transactional
    public void unlock(long meetingId, long sectionId, String user) {
        section(meetingId, sectionId);
        locks.findById(sectionId).filter(l -> l.isHeldBy(user) || !l.isLiveAt(time.now())).ifPresent(locks::delete);
    }

    /** Saves new content if nobody saved since {@code baseVersion}; returns the new version. */
    @Transactional
    public long save(long meetingId, long sectionId, long baseVersion, JsonNode content, String user, boolean admin) {
        MeetingSection s = section(meetingId, sectionId);
        Meeting m = s.getMeeting();
        requireEditable(m, admin);
        Instant now = time.now();

        SectionLock lock = locks.findById(sectionId).orElse(null);
        if (lock != null && lock.isLiveAt(now) && !lock.isHeldBy(user)) {
            throw lockedBy(lock);
        }
        if (s.getVersion() != baseVersion) {
            throw new VersionConflictException(new VersionConflictException.Current(
                    s.getVersion(), s.getContentJson(), s.getUpdatedBy(), s.getUpdatedAt()));
        }
        int columns = s.getColumnsSpec() == null ? 0 : s.getColumnsSpec().split("\\|").length;
        SectionContent.validate(s.getKind(), columns, kpiKeys(m, s.getSectionKey()), content);
        String newJson = content.toString();
        if (newJson.length() > SectionContent.MAX_CHARS) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "TOO_LARGE", "This section is too large to save.");
        }

        auditChanges(m.getId(), s.getSectionKey(), s.getContentJson(), content);
        s.setContent(newJson, user, now);
        m.touch(now);
        if (lock != null && lock.isHeldBy(user)) {
            lock.seen(now);
        }
        return sections.saveAndFlush(s).getVersion();
    }

    /** One audit row per changed top-level field, e.g. "roadmap.rows". */
    private void auditChanges(long meetingId, String key, String oldJson, JsonNode now) {
        JsonNode before;
        try {
            before = oldJson == null ? json.createObjectNode() : json.readTree(oldJson);
        } catch (Exception e) {
            before = json.createObjectNode();
        }
        Set<String> fields = new LinkedHashSet<>();
        before.fieldNames().forEachRemaining(fields::add);
        for (Iterator<String> it = now.fieldNames(); it.hasNext(); ) {
            fields.add(it.next());
        }
        for (String f : fields) {
            JsonNode a = before.get(f);
            JsonNode b = now.get(f);
            if (!Objects.equals(a, b)) {
                audit.recordChange("Meeting", meetingId, key + "." + f, a == null ? null : a.toString(), b == null ? null : b.toString());
            }
        }
    }

    /** KPI keys the Meeting's own Template version defines for this Section. */
    Set<String> kpiKeys(Meeting m, String sectionKey) {
        Set<String> keys = new LinkedHashSet<>();
        templates.version(m.getTemplateVersionNo()).getSections().stream()
                .filter(t -> t.getSectionKey().equals(sectionKey))
                .forEach(t -> t.getShownKpis().forEach(k -> keys.add(k.getKpiKey())));
        return keys;
    }

    private MeetingSection section(long meetingId, long sectionId) {
        meetings.live(meetingId);
        MeetingSection s = sections.findById(sectionId)
                .filter(x -> x.getMeeting().getId() == meetingId)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Section not found."));
        return s;
    }

    private static AccountException lockedBy(SectionLock lock) {
        return new AccountException(HttpStatus.CONFLICT, "LOCKED", "Being edited by " + lock.getUsername() + ".");
    }
}
