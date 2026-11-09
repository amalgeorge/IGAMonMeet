package com.igamonmeet.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.account.AccountException;
import com.igamonmeet.audit.AuditLog;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Themes (docs/spec-v2.md §4.1): colours, heading and body fonts, title/divider/content backgrounds and the logo, layered
 * over the House template. Settings are JSON; every save is a new version.
 */
@Service
public class ThemeService {

    public static final List<String> SAFE_FONTS = List.of("Arial", "Calibri", "Segoe UI", "Cambria", "Georgia", "Verdana");
    static final List<String> COLOR_KEYS = List.of("primary", "accent", "text", "background", "onTarget", "belowTarget", "offTarget");
    static final List<String> BACKGROUNDS = List.of("title", "divider", "content");
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Pattern HEX = Pattern.compile("[0-9A-Fa-f]{6}");

    public record ThemeView(long id, String name, boolean isDefault, int versionNo, JsonNode settings) {
    }

    public record Fonts(List<String> safe, List<String> brand) {
    }

    private final ThemeRepository themes;
    private final ThemeVersionRepository versions;
    private final ThemeImageRepository images;
    private final AuditLog audit;
    private final Clock clock;
    private final ObjectMapper json;
    private final List<String> brandFonts;

    public ThemeService(ThemeRepository themes, ThemeVersionRepository versions, ThemeImageRepository images, AuditLog audit, Clock clock,
                        ObjectMapper json, @Value("${app.brand-fonts:}") String brandFonts) {
        this.themes = themes;
        this.versions = versions;
        this.images = images;
        this.audit = audit;
        this.clock = clock;
        this.json = json;
        this.brandFonts = Arrays.stream(brandFonts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Transactional(readOnly = true)
    public List<ThemeView> list() {
        List<ThemeView> out = new ArrayList<>();
        for (Theme t : themes.findAllByOrderByNameAsc()) {
            out.add(view(t));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ThemeView get(long id) {
        return view(theme(id));
    }

    @Transactional(readOnly = true)
    public ThemeView defaultTheme() {
        return view(themes.findFirstByDefaultThemeTrue().orElseGet(() -> themes.findAllByOrderByNameAsc().get(0)));
    }

    @Transactional
    public ThemeView create(String name, JsonNode settings, String admin) {
        String clean = name(name);
        check(!themes.existsByNameIgnoreCase(clean), "A Theme called '" + clean + "' already exists.");
        Theme t = themes.save(new Theme(clean, admin, clock.instant()));
        versions.save(new ThemeVersion(t.getId(), 1, normalise(settings).toString(), admin, clock.instant()));
        audit.record("THEME_CREATED", "Theme", t.getId(), clean);
        return view(t);
    }

    /** Saves a new version. {@code basedOnVersion} guards against two Admins overwriting each other. */
    @Transactional
    public ThemeView update(long id, String name, JsonNode settings, int basedOnVersion, String admin) {
        Theme t = theme(id);
        int current = versions.findFirstByThemeIdOrderByVersionNoDesc(id).map(ThemeVersion::getVersionNo).orElse(0);
        if (basedOnVersion != current) {
            throw new AccountException(HttpStatus.CONFLICT, "THEME_CHANGED",
                    "Someone saved version " + current + " of this Theme while you were editing. Reload and apply your changes again.");
        }
        String clean = name(name);
        if (!clean.equalsIgnoreCase(t.getName())) {
            check(!themes.existsByNameIgnoreCase(clean), "A Theme called '" + clean + "' already exists.");
            t.rename(clean);
        }
        versions.save(new ThemeVersion(id, current + 1, normalise(settings).toString(), admin, clock.instant()));
        audit.record("THEME_SAVED", "Theme", id, clean + " v" + (current + 1));
        return view(t);
    }

    @Transactional
    public void makeDefault(long id, String admin) {
        Theme t = theme(id);
        themes.findAll().forEach(x -> x.setDefaultTheme(x.getId().equals(t.getId())));
        audit.record("THEME_DEFAULT", "Theme", id, t.getName());
    }

    public Fonts fonts() {
        Set<String> installed = installedFonts();
        return new Fonts(SAFE_FONTS, brandFonts.stream().filter(installed::contains).toList());
    }

    /**
     * Stores a PNG or JPG. Backgrounds are centre-cropped to 16:9 (spec-v2 §4.1); logos keep their shape.
     */
    @Transactional
    public long upload(byte[] file, String kind, String admin) {
        check(file != null && file.length > 0, "Choose an image file.");
        check(file.length <= MAX_IMAGE_BYTES, "Images can be up to 5 MB.");
        String type = sniff(file);
        check(type != null, "Upload a PNG or JPG image.");
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(file));
        } catch (Exception e) {
            img = null;
        }
        check(img != null, "That image could not be read.");
        byte[] out = file;
        if ("background".equals(kind)) {
            int w = img.getWidth();
            int h = img.getHeight();
            int cw = w;
            int ch = (int) Math.round(w * 9 / 16.0);
            if (ch > h) {
                ch = h;
                cw = (int) Math.round(h * 16 / 9.0);
            }
            if (cw != w || ch != h) {
                BufferedImage crop = img.getSubimage((w - cw) / 2, (h - ch) / 2, cw, ch);
                BufferedImage copy = new BufferedImage(cw, ch, "image/png".equals(type) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                copy.getGraphics().drawImage(crop, 0, 0, null);
                img = copy;
                out = encode(copy, type);
            }
        }
        ThemeImage saved = images.save(new ThemeImage(type, out, img.getWidth(), img.getHeight(), admin, clock.instant()));
        audit.record("THEME_IMAGE_UPLOADED", "ThemeImage", saved.getId(), kind);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public ThemeImage image(long id) {
        return images.findById(id).orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such image."));
    }

    /**
     * The House template theme with this Theme's settings applied, in the shape the in-app slide renderer reads:
     * colours (accent1, dk1, lt1), fonts, per-layout background colour or image, status colours and logo.
     */
    public String merged(String houseTheme, JsonNode settings) {
        ObjectNode t;
        try {
            t = houseTheme == null || houseTheme.isBlank() ? json.createObjectNode() : (ObjectNode) json.readTree(houseTheme);
        } catch (Exception e) {
            t = json.createObjectNode();
        }
        JsonNode c = settings.path("colors");
        ObjectNode colors = t.with("colors");
        colors.put("accent1", c.path("primary").asText("C8102E").toUpperCase());
        colors.put("accent2", c.path("accent").asText("ED1B2E").toUpperCase());
        colors.put("dk1", c.path("text").asText("1A1A1A").toUpperCase());
        colors.put("lt1", c.path("background").asText("FFFFFF").toUpperCase());
        ObjectNode status = t.putObject("statusColors");
        status.put("ON_TARGET", c.path("onTarget").asText("1E7D43").toUpperCase());
        status.put("BELOW_TARGET", c.path("belowTarget").asText("A35D00").toUpperCase());
        status.put("OFF_TARGET", c.path("offTarget").asText("C8102E").toUpperCase());
        t.put("headFont", settings.path("fonts").path("heading").asText("Arial"));
        t.put("bodyFont", settings.path("fonts").path("body").asText("Arial"));
        ObjectNode layouts = t.with("layouts");
        for (String layout : List.of("IAM_TITLE", "IAM_SECTION", "IAM_SUMMARY", "IAM_KPI", "IAM_CHART", "IAM_TABLE", "IAM_TWO_COLUMN", "IAM_CLOSING")) {
            String kind = "IAM_TITLE".equals(layout) ? "title" : "IAM_SECTION".equals(layout) || "IAM_CLOSING".equals(layout) ? "divider" : "content";
            JsonNode bg = settings.path("backgrounds").path(kind);
            ObjectNode l = layouts.with(layout);
            if (bg.hasNonNull("color")) {
                l.put("background", bg.get("color").asText().toUpperCase());
            }
            if (bg.hasNonNull("imageId")) {
                l.put("backgroundImage", "api/theme-images/" + bg.get("imageId").asLong());
            } else {
                l.remove("backgroundImage");
            }
            l.put("backgroundKind", kind);
        }
        ObjectNode logo = t.putObject("logo");
        logo.put("show", settings.path("logo").path("show").asBoolean(true));
        if (settings.path("logo").hasNonNull("imageId")) {
            logo.put("image", "api/theme-images/" + settings.path("logo").get("imageId").asLong());
        }
        return t.toString();
    }

    /** Checks and tidies settings: hex colours, allowed fonts, existing images. Missing parts get the defaults. */
    ObjectNode normalise(JsonNode in) {
        check(in != null && in.isObject(), "Theme settings must be an object.");
        ObjectNode out = json.createObjectNode();
        ObjectNode colors = out.putObject("colors");
        Map<String, String> defaults = Map.of("primary", "C8102E", "accent", "ED1B2E", "text", "1A1A1A", "background", "FFFFFF",
                "onTarget", "1E7D43", "belowTarget", "A35D00", "offTarget", "C8102E");
        for (String k : COLOR_KEYS) {
            String v = in.path("colors").path(k).asText(defaults.get(k)).replace("#", "");
            check(HEX.matcher(v).matches(), "Colour '" + k + "' must be a 6-digit hex value such as C8102E.");
            colors.put(k, v.toUpperCase());
        }
        Set<String> allowed = new HashSet<>(SAFE_FONTS);
        allowed.addAll(fonts().brand());
        ObjectNode fonts = out.putObject("fonts");
        for (String k : List.of("heading", "body")) {
            String f = in.path("fonts").path(k).asText("Arial");
            check(allowed.contains(f), "Font '" + f + "' is not available. Use a safe font or a brand font installed on the server.");
            fonts.put(k, f);
        }
        ObjectNode bgs = out.putObject("backgrounds");
        for (String k : BACKGROUNDS) {
            JsonNode b = in.path("backgrounds").path(k);
            ObjectNode o = bgs.putObject(k);
            String color = b.path("color").asText("title".equals(k) || "divider".equals(k) ? colors.get("primary").asText() : colors.get("background").asText());
            color = color.replace("#", "");
            check(HEX.matcher(color).matches(), "Background colour for " + k + " slides must be a 6-digit hex value.");
            o.put("color", color.toUpperCase());
            if (b.hasNonNull("imageId")) {
                check(images.existsById(b.get("imageId").asLong()), "The " + k + " background image no longer exists.");
                o.put("imageId", b.get("imageId").asLong());
            }
        }
        ObjectNode logo = out.putObject("logo");
        logo.put("show", in.path("logo").path("show").asBoolean(true));
        if (in.path("logo").hasNonNull("imageId")) {
            check(images.existsById(in.path("logo").get("imageId").asLong()), "The logo image no longer exists.");
            logo.put("imageId", in.path("logo").get("imageId").asLong());
        }
        return out;
    }

    public JsonNode settings(ThemeVersion v) {
        try {
            return json.readTree(v.getSettingsJson());
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private ThemeView view(Theme t) {
        ThemeVersion v = versions.findFirstByThemeIdOrderByVersionNoDesc(t.getId()).orElseThrow();
        return new ThemeView(t.getId(), t.getName(), t.isDefaultTheme(), v.getVersionNo(), settings(v));
    }

    private Theme theme(long id) {
        return themes.findById(id).orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such Theme."));
    }

    private static String name(String name) {
        String clean = name == null ? "" : name.trim();
        check(!clean.isEmpty() && clean.length() <= 100, "A Theme needs a name of up to 100 characters.");
        return clean;
    }

    private static Set<String> installedFonts() {
        try {
            return new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        } catch (Throwable e) {
            return Set.of();
        }
    }

    private static String sniff(byte[] f) {
        if (f.length > 8 && (f[0] & 0xFF) == 0x89 && f[1] == 'P' && f[2] == 'N' && f[3] == 'G') return "image/png";
        if (f.length > 3 && (f[0] & 0xFF) == 0xFF && (f[1] & 0xFF) == 0xD8 && (f[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    private static byte[] encode(BufferedImage img, String type) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "image/png".equals(type) ? "png" : "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "INVALID_THEME", message);
        }
    }
}
