package com.igamonmeet.settings;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SettingsController {

    public record Toggle(boolean enabled) {
    }

    private final Settings settings;

    public SettingsController(Settings settings) {
        this.settings = settings;
    }

    /** Public: only what the sign-in screen needs. */
    @GetMapping("/api/auth/features")
    public Map<String, Boolean> publicFeatures() {
        return Map.of("selfRegistration", settings.isOn(FeatureToggle.SELF_REGISTRATION),
                "forgotPassword", settings.isOn(FeatureToggle.FORGOT_PASSWORD));
    }

    /** Signed-in users: lets the UI hide switched-off features. */
    @GetMapping("/api/settings/features")
    public Map<FeatureToggle, Boolean> features() {
        return settings.all();
    }

    @PutMapping("/api/admin/settings/{toggle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@PathVariable FeatureToggle toggle, @RequestBody Toggle body, Authentication auth) {
        settings.set(toggle, body.enabled(), auth.getName());
    }
}
