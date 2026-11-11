package com.igamonmeet.settings;

import com.igamonmeet.audit.AuditLog;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and changes feature toggles. Every change is audit-logged. Missing rows count as on. */
@Service
public class Settings {

    private final AppSettingRepository repository;
    private final AuditLog audit;
    private final Clock clock;

    public Settings(AppSettingRepository repository, AuditLog audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    public boolean isOn(FeatureToggle toggle) {
        return repository.findById(toggle.name()).map(s -> Boolean.parseBoolean(s.getValue())).orElse(true);
    }

    public Map<FeatureToggle, Boolean> all() {
        Map<FeatureToggle, Boolean> map = new EnumMap<>(FeatureToggle.class);
        for (FeatureToggle t : FeatureToggle.values()) {
            map.put(t, isOn(t));
        }
        return map;
    }

    /** Global names for the three KPI statuses, renamed by Admin (docs/spec-v2.md §2.4). */
    public Map<String, String> statusLabels() {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String s : STATUSES) {
            map.put(s, repository.findById("STATUS_LABEL_" + s).map(AppSetting::getValue).orElse(DEFAULT_LABELS.get(s)));
        }
        return map;
    }

    @Transactional
    public void setStatusLabels(Map<String, String> labels, String admin) {
        for (String s : STATUSES) {
            String v = labels.get(s);
            if (v == null) continue;
            String clean = v.trim();
            if (clean.isEmpty() || clean.length() > 30) {
                throw new com.igamonmeet.account.AccountException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_LABEL",
                        "Each status label needs 1 to 30 characters.");
            }
            AppSetting row = repository.findById("STATUS_LABEL_" + s).orElseThrow();
            String old = row.getValue();
            if (!old.equals(clean)) {
                row.update(clean, admin, clock.instant());
                audit.recordChange("Setting", "STATUS_LABEL_" + s, "label", old, clean);
            }
        }
    }

    /** Types offered in the SITREP Type column (docs/spec-v3.md §1.3); editors may still type others. */
    public List<String> sitrepTypes() {
        String v = repository.findById("SITREP_TYPES").map(AppSetting::getValue).orElse("");
        return v.isBlank() ? List.of() : List.of(v.split("\\|"));
    }

    @Transactional
    public void setSitrepTypes(List<String> types, String admin) {
        List<String> clean = types == null ? List.of() : types.stream().map(String::trim).filter(t -> !t.isEmpty()).distinct().toList();
        if (clean.size() > 20 || clean.stream().anyMatch(t -> t.length() > 40 || t.contains("|"))) {
            throw new com.igamonmeet.account.AccountException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_TYPES",
                    "Give up to 20 SITREP types of up to 40 characters, without '|'.");
        }
        AppSetting row = repository.findById("SITREP_TYPES").orElseThrow();
        String old = row.getValue();
        String now = String.join("|", clean);
        if (!old.equals(now)) {
            row.update(now, admin, clock.instant());
            audit.recordChange("Setting", "SITREP_TYPES", "types", old, now);
        }
    }

    /** One-time upgrade markers, e.g. UPGRADE_SITREP_DONE. */
    public boolean flag(String key) {
        return repository.findById(key).map(s -> Boolean.parseBoolean(s.getValue())).orElse(false);
    }

    /**
     * Holds the flag's row until the caller's transaction ends, so two servers starting together run a one-time
     * upgrade one after the other; the second then finds the first one's work and adds nothing.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockFlag(String key) {
        repository.lockByKey(key);
    }

    @Transactional
    public void setFlag(String key, String by) {
        repository.findById(key).ifPresent(s -> s.update("true", by, clock.instant()));
    }

    private static final List<String> STATUSES = List.of("ON_TARGET", "BELOW_TARGET", "OFF_TARGET");
    private static final Map<String, String> DEFAULT_LABELS = Map.of("ON_TARGET", "On target", "BELOW_TARGET", "Below target", "OFF_TARGET", "Off target");

    @Transactional
    public void set(FeatureToggle toggle, boolean on, String admin) {
        AppSetting s = repository.findById(toggle.name()).orElseThrow();
        boolean old = Boolean.parseBoolean(s.getValue());
        if (old == on) {
            return;
        }
        s.update(Boolean.toString(on), admin, clock.instant());
        audit.recordChange("Setting", toggle.name(), "enabled", old, on);
    }
}
