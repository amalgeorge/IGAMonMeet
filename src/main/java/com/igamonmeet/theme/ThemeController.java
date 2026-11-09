package com.igamonmeet.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.igamonmeet.house.HouseTemplateService;
import com.igamonmeet.theme.ThemeService.Fonts;
import com.igamonmeet.theme.ThemeService.ThemeView;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ThemeController {

    public record ThemeRequest(String name, @NotNull JsonNode settings, Integer basedOnVersion) {
    }

    private final ThemeService themes;
    private final HouseTemplateService house;
    private final MeetingThemes meetingThemes;

    public record ChooseTheme(long themeId) {
    }

    public ThemeController(ThemeService themes, HouseTemplateService house, MeetingThemes meetingThemes) {
        this.themes = themes;
        this.house = house;
        this.meetingThemes = meetingThemes;
    }

    @GetMapping("/api/themes")
    public List<ThemeView> list() {
        return themes.list();
    }

    /** Merged House template + the Meeting's Theme version (ADR 0008) for in-app slides. */
    @GetMapping(value = "/api/meetings/{id}/theme", produces = MediaType.APPLICATION_JSON_VALUE)
    public String meetingTheme(@PathVariable long id) {
        return themes.merged(house.currentTheme(), meetingThemes.settings(id));
    }

    @GetMapping("/api/meetings/{id}/theme-choice")
    public MeetingThemes.Choice choice(@PathVariable long id, Authentication auth) {
        return meetingThemes.choice(id, isAdmin(auth));
    }

    @PutMapping("/api/meetings/{id}/theme-choice")
    public MeetingThemes.Choice choose(@PathVariable long id, @RequestBody ChooseTheme r, Authentication auth) {
        return meetingThemes.choose(id, r.themeId(), auth.getName(), isAdmin(auth));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping("/api/theme-images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        ThemeImage img = themes.image(id);
        // Images never change once stored, so browsers may keep them.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate()).body(img.getContent());
    }

    @GetMapping("/api/admin/themes/fonts")
    public Fonts fonts() {
        return themes.fonts();
    }

    /** Preview: the merged theme for unsaved settings, so Admin sees sample slides before saving. */
    @PostMapping(value = "/api/admin/themes/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public String preview(@RequestBody JsonNode settings) {
        return themes.merged(house.currentTheme(), themes.normalise(settings));
    }

    @PostMapping("/api/admin/themes")
    @ResponseStatus(HttpStatus.CREATED)
    public ThemeView create(@RequestBody ThemeRequest r, Authentication auth) {
        return themes.create(r.name(), r.settings(), auth.getName());
    }

    @PutMapping("/api/admin/themes/{id}")
    public ThemeView update(@PathVariable long id, @RequestBody ThemeRequest r, Authentication auth) {
        return themes.update(id, r.name(), r.settings(), r.basedOnVersion() == null ? -1 : r.basedOnVersion(), auth.getName());
    }

    @PostMapping("/api/admin/themes/{id}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void makeDefault(@PathVariable long id, Authentication auth) {
        themes.makeDefault(id, auth.getName());
    }

    @PostMapping("/api/admin/theme-images")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> upload(@RequestParam("file") MultipartFile file, @RequestParam("kind") String kind, Authentication auth) throws IOException {
        return Map.of("id", themes.upload(file.getBytes(), "logo".equals(kind) ? "logo" : "background", auth.getName()));
    }
}
