package com.igamonmeet.pptximport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.meeting.ActionItem;
import com.igamonmeet.meeting.ActionsAndComments;
import com.igamonmeet.meeting.Meeting;
import com.igamonmeet.meeting.MeetingController.SectionView;
import com.igamonmeet.meeting.MeetingDetails;
import com.igamonmeet.meeting.MeetingService;
import com.igamonmeet.meeting.MeetingTime;
import com.igamonmeet.meeting.SectionEditor;
import com.igamonmeet.pptximport.PptxReader.RawSlide;
import com.igamonmeet.pptximport.SlideMatcher.Suggestion;
import com.igamonmeet.pptximport.SlideMatcher.Target;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upload, suggest a Section per slide, then apply the confirmed mapping (docs/spec.md §6.1, ADR 0005). */
@Service
public class ImportService {

    public enum Mode { KEEP, REPLACE }

    public record Proposal(long importId, String fileName, List<Suggestion> slides, int pictures, int charts) {
    }

    public record Mapping(int n, String sectionKey) {
    }

    public record Skipped(String sectionKey, String reason) {
    }

    public record Result(List<String> applied, List<String> keptWithNote, List<Skipped> skipped, int actionItemsAdded,
                         int picturesNotImported, int chartsNotImported) {
    }

    private final PptxReader reader;
    private final SlideMatcher matcher;
    private final ContentExtractor extractor;
    private final ImportRecordRepository records;
    private final MeetingService meetings;
    private final MeetingDetails details;
    private final SectionEditor editor;
    private final ActionsAndComments actions;
    private final MeetingTime time;
    private final AuditLog audit;
    private final ObjectMapper json;

    public ImportService(PptxReader reader, SlideMatcher matcher, ContentExtractor extractor, ImportRecordRepository records,
                         MeetingService meetings, MeetingDetails details, SectionEditor editor, ActionsAndComments actions,
                         MeetingTime time, AuditLog audit, ObjectMapper json) {
        this.reader = reader;
        this.matcher = matcher;
        this.extractor = extractor;
        this.records = records;
        this.meetings = meetings;
        this.details = details;
        this.editor = editor;
        this.actions = actions;
        this.time = time;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public Proposal upload(long meetingId, String fileName, byte[] file, String user, boolean admin) {
        Meeting m = meetings.live(meetingId);
        editor.requireEditable(m, admin);
        List<RawSlide> slides = reader.read(fileName, file);
        String name = fileName.replaceAll("[\\\\/]", "_");
        ImportRecord rec = records.save(new ImportRecord(meetingId, name.length() > 255 ? name.substring(0, 255) : name, file, slides.size(), user, time.now()));
        List<Target> targets = details.detail(meetingId, admin).sections().stream()
                .map(s -> new Target(s.key(), s.name(), s.layout())).collect(Collectors.toList());
        List<Suggestion> suggestions = slides.stream().map(s -> matcher.suggest(s, targets)).collect(Collectors.toList());
        audit.record("IMPORT_UPLOADED", "Meeting", meetingId, name + ", " + slides.size() + " slides");
        return new Proposal(rec.getId(), rec.getFileName(), suggestions,
                slides.stream().mapToInt(RawSlide::pictures).sum(), slides.stream().mapToInt(RawSlide::charts).sum());
    }

    @Transactional
    public Result apply(long meetingId, long importId, Mode mode, List<Mapping> mappings, String user, boolean admin) {
        Meeting m = meetings.live(meetingId);
        editor.requireEditable(m, admin);
        ImportRecord rec = records.findById(importId).filter(r -> r.getMeetingId() == meetingId)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Import not found."));
        if (rec.getAppliedAt() != null) {
            throw new AccountException(HttpStatus.CONFLICT, "ALREADY_APPLIED", "This file was already applied. Upload it again to re-import.");
        }
        List<RawSlide> slides = reader.read(rec.getFileName(), rec.getContent());
        Map<String, SectionView> sections = new LinkedHashMap<>();
        details.detail(meetingId, admin).sections().forEach(s -> sections.put(s.key(), s));

        Map<String, List<RawSlide>> bySection = new LinkedHashMap<>();
        for (Mapping mp : mappings) {
            if (mp.sectionKey() == null || mp.n() < 1 || mp.n() > slides.size()) continue;
            if (!sections.containsKey(mp.sectionKey())) {
                throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID", "Unknown section " + mp.sectionKey() + ".");
            }
            bySection.computeIfAbsent(mp.sectionKey(), k -> new ArrayList<>()).add(slides.get(mp.n() - 1));
        }

        List<String> applied = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        int added = 0;
        for (Map.Entry<String, List<RawSlide>> e : bySection.entrySet()) {
            SectionView s = sections.get(e.getKey());
            try {
                if (s.kind() == SectionKind.ACTION_ITEMS) {
                    added += addActions(meetingId, e.getValue(), admin);
                    applied.add(s.key());
                    continue;
                }
                ObjectNode imported = extractor.extract(s.kind(), s.columns(), s.kpis(), e.getValue());
                boolean hasContent = s.contentJson() != null && s.filled();
                if (mode == Mode.KEEP && hasContent) {
                    ObjectNode mine = (ObjectNode) json.readTree(s.contentJson());
                    String note = "Imported from slides " + e.getValue().stream().map(r -> String.valueOf(r.n())).collect(Collectors.joining(", "))
                            + " of " + rec.getFileName() + ":\n" + summary(e.getValue());
                    String existing = mine.path("notes").asText("");
                    String combined = (existing.isBlank() ? "" : existing + "\n\n") + note;
                    mine.put("notes", combined.length() > 4000 ? combined.substring(0, 4000) : combined);
                    editor.save(meetingId, s.id(), s.version(), mine, user, admin);
                    kept.add(s.key());
                } else {
                    if (mode == Mode.KEEP && s.contentJson() != null) {
                        // Keep the user's own notes when replacing empty content.
                        JsonNode old = json.readTree(s.contentJson());
                        if (old.hasNonNull("notes") && !imported.has("notes")) imported.set("notes", old.get("notes"));
                    }
                    editor.save(meetingId, s.id(), s.version(), imported, user, admin);
                    applied.add(s.key());
                }
            } catch (AccountException ex) {
                skipped.add(new Skipped(s.key(), ex.getMessage()));
            } catch (Exception ex) {
                skipped.add(new Skipped(s.key(), "Could not read this slide's content."));
            }
        }
        int pictures = bySection.values().stream().flatMap(List::stream).mapToInt(RawSlide::pictures).sum();
        int charts = bySection.values().stream().flatMap(List::stream).mapToInt(RawSlide::charts).sum();
        Result result = new Result(applied, kept, skipped, added, pictures, charts);
        rec.applied(user, time.now(), "applied=" + applied + " kept=" + kept + " skipped=" + skipped.size() + " actions=" + added);
        audit.record("IMPORT_APPLIED", "Meeting", meetingId, rec.getFileName() + " (" + mode + "): " + applied.size() + " replaced, "
                + kept.size() + " kept with note, " + skipped.size() + " skipped");
        return result;
    }

    /** Adds rows as new Action items, skipping titles the Meeting already has. Never deletes items. */
    private int addActions(long meetingId, List<RawSlide> slides, boolean admin) {
        Set<String> existing = actions.ordered(meetingId).stream().map(a -> a.getTitle().trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        int added = 0;
        for (List<String> r : extractor.actionRows(slides)) {
            String title = r.get(0).trim();
            if (title.isEmpty() || existing.contains(title.toLowerCase(Locale.ROOT))) continue;
            String owner = r.size() > 1 && !r.get(1).isBlank() ? r.get(1).trim() : "Unassigned";
            actions.add(meetingId, title.length() > 500 ? title.substring(0, 500) : title, owner.length() > 200 ? owner.substring(0, 200) : owner,
                    team(r.size() > 2 ? r.get(2) : ""), null, admin);
            existing.add(title.toLowerCase(Locale.ROOT));
            added++;
        }
        return added;
    }

    private static TeamTag team(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        return t.startsWith("eng") ? TeamTag.ENGINEERING : t.startsWith("op") ? TeamTag.OPERATIONS : TeamTag.SHARED;
    }

    private static String summary(List<RawSlide> slides) {
        String text = slides.stream().map(s -> {
            List<String> lines = new ArrayList<>(s.texts());
            s.table().forEach(row -> lines.add(String.join(" | ", row)));
            return String.join("\n", lines);
        }).collect(Collectors.joining("\n---\n"));
        return text.length() > 3000 ? text.substring(0, 3000) + "…" : text;
    }

    public List<ImportRecord> history(long meetingId) {
        return records.findByMeetingIdOrderByUploadedAtDesc(meetingId);
    }

    static boolean isOpen(ActionItem a) {
        return ActionItem.OPEN.equals(a.getStatus());
    }
}
