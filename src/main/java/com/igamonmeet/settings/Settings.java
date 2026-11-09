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
