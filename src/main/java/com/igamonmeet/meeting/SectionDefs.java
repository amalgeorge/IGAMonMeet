package com.igamonmeet.meeting;

import com.igamonmeet.template.TemplateKpi;
import com.igamonmeet.template.TemplateSection;
import com.igamonmeet.template.TemplateService;
import com.igamonmeet.template.Visibility;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Finds the Template definition behind a Meeting Section. A Section switched on later (ticket 20) is defined by the
 * Template version it came from; every other Section by the Meeting's own version.
 */
@Component
public class SectionDefs {

    private final TemplateService templates;

    public SectionDefs(TemplateService templates) {
        this.templates = templates;
    }

    /** A per-call lookup; call {@link #lookup()} once per request and reuse it for every Section. */
    public Lookup lookup() {
        return new Lookup();
    }

    public final class Lookup {
        private final Map<Integer, Map<String, TemplateSection>> byVersion = new HashMap<>();

        public Optional<TemplateSection> def(Meeting m, MeetingSection s) {
            int v = s.templateVersionNo(m.getTemplateVersionNo());
            return Optional.ofNullable(byVersion.computeIfAbsent(v, n -> templates.version(n).getSections().stream()
                    .collect(Collectors.toMap(TemplateSection::getSectionKey, t -> t))).get(s.getSectionKey()));
        }

        /** KPIs this Section shows in this Meeting: the Shown ones plus any Available ones switched on. */
        public List<TemplateKpi> kpis(Meeting m, MeetingSection s) {
            List<String> enabled = s.getEnabledKpis();
            return def(m, s).map(d -> d.getKpis().stream()
                            .filter(k -> k.getVisibility() == Visibility.SHOWN || enabled.contains(k.getKpiKey()))
                            .collect(Collectors.toList()))
                    .orElse(List.of());
        }
    }
}
