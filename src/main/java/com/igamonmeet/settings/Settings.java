package com.igamonmeet.settings;

import com.igamonmeet.audit.AuditLog;
import java.time.Clock;
import java.util.EnumMap;
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
