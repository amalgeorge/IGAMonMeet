package com.igamonmeet.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.meeting.Meeting;
import com.igamonmeet.meeting.MeetingRepository;
import com.igamonmeet.meeting.MeetingService;
import com.igamonmeet.meeting.MeetingTime;
import com.igamonmeet.meeting.SectionEditor;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which Theme version a Meeting's slides use (docs/spec-v2.md §4.2-4.3, ADR 0008): the latest version while the Meeting
 * is Preparing; from the Meeting date, the version in force at the start of that day (SGT), unless an Admin pinned one.
 */
@Service
public class MeetingThemes {

    public record Choice(long themeId, String themeName, int versionNo, boolean frozen, boolean canChange, List<ThemeService.ThemeView> themes) {
    }

    private final MeetingService meetings;
    private final MeetingRepository repo;
    private final ThemeService themes;
    private final ThemeRepository themeRepo;
    private final ThemeVersionRepository versions;
    private final MeetingTime time;
    private final SectionEditor editor;
    private final AuditLog audit;

    public MeetingThemes(MeetingService meetings, MeetingRepository repo, ThemeService themes, ThemeRepository themeRepo,
                         ThemeVersionRepository versions, MeetingTime time, SectionEditor editor, AuditLog audit) {
        this.meetings = meetings;
        this.repo = repo;
        this.themes = themes;
        this.themeRepo = themeRepo;
        this.versions = versions;
        this.time = time;
        this.editor = editor;
        this.audit = audit;
    }

    /** The Theme version this Meeting shows now. */
    @Transactional(readOnly = true)
    public ThemeVersion version(Meeting m) {
        long themeId = m.getThemeId() != null && themeRepo.existsById(m.getThemeId()) ? m.getThemeId() : themes.defaultTheme().id();
        if (m.getThemeVersionNo() != null) {
            var pinned = versions.findByThemeIdAndVersionNo(themeId, m.getThemeVersionNo());
            if (pinned.isPresent()) return pinned.get();
        }
        if (held(m)) {
            Instant dayStart = m.getMeetingDate().atStartOfDay(MeetingTime.SGT).toInstant();
            return versions.findFirstByThemeIdAndCreatedAtBeforeOrderByVersionNoDesc(themeId, dayStart)
                    .or(() -> versions.findFirstByThemeIdOrderByVersionNoAsc(themeId)).orElseThrow();
        }
        return versions.findFirstByThemeIdOrderByVersionNoDesc(themeId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public JsonNode settings(long meetingId) {
        return themes.settings(version(meetings.live(meetingId)));
    }

    @Transactional(readOnly = true)
    public Choice choice(long meetingId, boolean admin) {
        Meeting m = meetings.live(meetingId);
        ThemeVersion v = version(m);
        String name = themeRepo.findById(v.getThemeId()).map(Theme::getName).orElse("");
        boolean canChange = admin || (!held(m) && time.editWindowOpen(m.getMeetingDate()));
        return new Choice(v.getThemeId(), name, v.getVersionNo(), held(m), canChange, themes.list());
    }

    /** Admin any time; any editor while the Meeting is Preparing (Q22). On a Held Meeting the chosen Theme is pinned at its latest version. */
    @Transactional
    public Choice choose(long meetingId, long themeId, String user, boolean admin) {
        Meeting m = meetings.live(meetingId);
        editor.requireEditable(m, admin);
        if (held(m) && !admin) {
            throw new AccountException(HttpStatus.CONFLICT, "MEETING_HELD", "This Meeting has been held; only an Admin can change its Theme.");
        }
        Theme t = themeRepo.findById(themeId).orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such Theme."));
        Integer pin = held(m) ? versions.findFirstByThemeIdOrderByVersionNoDesc(themeId).map(ThemeVersion::getVersionNo).orElse(null) : null;
        m.useTheme(themeId, pin);
        repo.save(m);
        audit.record("MEETING_THEME", "Meeting", meetingId, t.getName() + (pin == null ? "" : " v" + pin) + " by " + user);
        return choice(meetingId, admin);
    }

    private boolean held(Meeting m) {
        return "HELD".equals(time.label(m.getMeetingDate()));
    }
}
