package com.igamonmeet.pptximport;

import com.igamonmeet.pptximport.PptxReader.RawSlide;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Suggests which Section each slide belongs to, with a confidence the user confirms (docs/spec.md §6.1). */
@Component
public class SlideMatcher {

    public record Target(String key, String name, String layout) {
    }

    public record Suggestion(int n, String title, String layout, String sectionKey, double confidence, String reason) {
    }

    /** Small vocabulary of common slide-title words to Section-name words. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("kpi", "metrics"), Map.entry("kpis", "metrics"), Map.entry("measures", "metrics"),
            Map.entry("uar", "reviews"), Map.entry("certification", "reviews"), Map.entry("recertification", "reviews"),
            Map.entry("actions", "action"), Map.entry("todo", "action"), Map.entry("open", "action"),
            Map.entry("risk", "risks"), Map.entry("blockers", "risks"), Map.entry("decision", "decisions"),
            Map.entry("overview", "summary"), Map.entry("highlights", "summary"),
            Map.entry("incident", "sitrep"), Map.entry("incidents", "sitrep"), Map.entry("problems", "sitrep"),
            Map.entry("release", "releases"), Map.entry("plan", "roadmap"), Map.entry("projects", "roadmap"));
    private static final Set<String> SKIP_LAYOUTS = Set.of("IAM_TITLE", "IAM_SECTION", "IAM_CLOSING");

    /** "Roadmap (2 of 3)", "Roadmap (cont.)", "Appendix: Roadmap" all become "Roadmap". */
    public static String cleanTitle(String title) {
        return title.replaceAll("(?i)^appendix:\\s*", "")
                .replaceAll("(?i)\\s*\\((\\d+\\s*(of|/)\\s*\\d+|cont\\.?|continued)\\)\\s*$", "")
                .replaceAll("(?i)\\s*[-–]\\s*(cont\\.?|continued)$", "")
                .trim();
    }

    public Suggestion suggest(RawSlide s, List<Target> targets) {
        String title = cleanTitle(s.title());
        if (SKIP_LAYOUTS.contains(s.layout()) || s.n() == 1 && !s.layout().startsWith("IAM_")) {
            return new Suggestion(s.n(), s.title(), s.layout(), null, 1.0, "Title, divider or closing slide");
        }
        if (s.texts().contains("Not provided yet.")) {
            return new Suggestion(s.n(), s.title(), s.layout(), null, 1.0, "Empty in the source deck");
        }
        Target best = null;
        double bestScore = 0;
        for (Target t : targets) {
            double score = score(title, t.name());
            if (s.layout().equals(t.layout())) {
                score = score >= 1.0 ? 1.0 : Math.min(0.95, score + 0.2);
            }
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        if (best == null || bestScore < 0.5) {
            return new Suggestion(s.n(), s.title(), s.layout(), null, bestScore, "No matching section");
        }
        return new Suggestion(s.n(), s.title(), s.layout(), best.key(), Math.round(bestScore * 100) / 100.0,
                bestScore >= 1.0 ? "Exact title match" : bestScore >= 0.8 ? "Close match" : "Check this match");
    }

    static double score(String title, String sectionName) {
        Set<String> a = words(title);
        Set<String> b = words(sectionName);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        Set<String> both = new HashSet<>(a);
        both.retainAll(b);
        Set<String> any = new HashSet<>(a);
        any.addAll(b);
        return (double) both.size() / any.size();
    }

    private static Set<String> words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(w -> w.length() > 1 && !Set.of("and", "the", "of", "for", "in", "ops", "eng", "iam").contains(w))
                .map(w -> SYNONYMS.getOrDefault(w, w))
                .collect(Collectors.toSet());
    }
}
