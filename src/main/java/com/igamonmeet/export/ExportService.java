package com.igamonmeet.export;

import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.house.HouseTemplateService;
import com.igamonmeet.meeting.Comment;
import com.igamonmeet.meeting.CommentRepository;
import com.igamonmeet.meeting.Meeting;
import com.igamonmeet.meeting.MeetingService;
import com.igamonmeet.meeting.MeetingTime;
import com.igamonmeet.slides.PlanModel.Plan;
import com.igamonmeet.slides.SlidePlanService;
import com.igamonmeet.theme.MeetingThemes;
import com.igamonmeet.theme.ThemeService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PPTX export of a Meeting on the current House template (docs/spec.md §6.2). */
@Service
public class ExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(MeetingTime.SGT);

    public record Request(List<String> sections, boolean notes, boolean comments, boolean versionStamp) {
    }

    public record File(String name, byte[] content) {
    }

    private final SlidePlanService plans;
    private final HouseTemplateService house;
    private final PptxWriter writer;
    private final MeetingService meetings;
    private final CommentRepository comments;
    private final MeetingTime time;
    private final AuditLog audit;
    private final MeetingThemes meetingThemes;
    private final ThemeService themes;

    public ExportService(SlidePlanService plans, HouseTemplateService house, PptxWriter writer, MeetingService meetings,
                         CommentRepository comments, MeetingTime time, AuditLog audit, MeetingThemes meetingThemes, ThemeService themes) {
        this.plans = plans;
        this.house = house;
        this.writer = writer;
        this.meetings = meetings;
        this.comments = comments;
        this.time = time;
        this.audit = audit;
        this.meetingThemes = meetingThemes;
        this.themes = themes;
    }

    @Transactional(readOnly = true)
    public File pptx(long meetingId, Request r) throws IOException {
        Meeting m = meetings.live(meetingId);
        Set<String> include = r.sections() == null ? null : Set.copyOf(r.sections());
        Plan plan = plans.plan(meetingId, include);
        int houseVersion = house.currentVersion();
        String stamp = r.versionStamp() ? "Exported " + STAMP.format(time.now()) + " SGT · House template v" + houseVersion : null;

        List<List<String>> commentRows = null;
        if (r.comments()) {
            Map<String, String> names = m.getSections().stream().collect(Collectors.toMap(s -> s.getSectionKey(), s -> s.getName()));
            commentRows = new ArrayList<>();
            List<Comment> all = comments.findByMeetingIdOrderByCreatedAtAscIdAsc(meetingId);
            for (Comment c : all) {
                if (c.getParentId() != null || (include != null && !include.contains(c.getSectionKey()))) {
                    continue;
                }
                StringBuilder text = new StringBuilder(c.getAuthor() + ": " + c.getBody());
                all.stream().filter(x -> c.getId().equals(x.getParentId()))
                        .forEach(x -> text.append("\n↳ ").append(x.getAuthor()).append(": ").append(x.getBody()));
                commentRows.add(List.of(names.getOrDefault(c.getSectionKey(), c.getSectionKey()), text.toString(),
                        c.isResolved() ? "Resolved" : "Open"));
            }
        }
        byte[] bytes = writer.write(house.file(houseVersion), plan, new PptxWriter.Options(r.notes(), stamp, commentRows, theme(meetingId)));
        audit.record("EXPORTED", "Meeting", meetingId, "PPTX, " + plan.slides().size() + " slides, House template v" + houseVersion);
        String name = m.getTitle().replaceAll("[^A-Za-z0-9 ._-]+", " ").trim().replaceAll("\\s+", "_") + ".pptx";
        return new File(name, bytes);
    }

    /** The Meeting's Theme (pinned, held-by-date or latest; ADR 0008) in the shape the PPTX writer needs. */
    private ThemeSpec theme(long meetingId) {
        JsonNode t = meetingThemes.settings(meetingId);
        if (t == null || t.isEmpty()) {
            return null;
        }
        JsonNode c = t.path("colors");
        Map<String, String> status = new HashMap<>();
        status.put("ON_TARGET", hex(c, "onTarget"));
        status.put("BELOW_TARGET", hex(c, "belowTarget"));
        status.put("OFF_TARGET", hex(c, "offTarget"));
        status.values().removeIf(v -> v == null);
        Map<String, ThemeSpec.Background> backgrounds = new HashMap<>();
        for (String kind : List.of("title", "divider", "content")) {
            JsonNode b = t.path("backgrounds").path(kind);
            if (!b.isMissingNode()) {
                backgrounds.put(kind, new ThemeSpec.Background(hex(b, "color"), image(b.path("imageId"))));
            }
        }
        return new ThemeSpec(hex(c, "primary"), hex(c, "accent"), hex(c, "text"), hex(c, "background"), status,
                t.path("fonts").path("heading").asText(null), t.path("fonts").path("body").asText(null), backgrounds,
                image(t.path("logo").path("imageId")), t.path("logo").path("show").asBoolean(true));
    }

    private static String hex(JsonNode n, String key) {
        String v = n.path(key).asText(null);
        return v != null && v.matches("[0-9A-Fa-f]{6}") ? v.toUpperCase() : null;
    }

    private ThemeSpec.Image image(JsonNode id) {
        if (id == null || !id.canConvertToLong()) {
            return null;
        }
        try {
            var img = themes.image(id.asLong());
            return new ThemeSpec.Image(img.getContent(), img.getContentType());
        } catch (RuntimeException gone) {
            return null;
        }
    }
}
