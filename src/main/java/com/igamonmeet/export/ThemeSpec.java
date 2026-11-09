package com.igamonmeet.export;

import java.util.Map;

/**
 * A Meeting's Theme as the PPTX writer needs it (docs/spec-v2.md §4.4): hex colours, fonts, per-slide-kind backgrounds
 * (colour, optional image) and an optional logo image. {@code null} fields keep the House template's own look.
 */
public record ThemeSpec(String primary, String accent, String text, String background, Map<String, String> statusColors,
                        String headFont, String bodyFont, Map<String, Background> backgrounds, Image logo, boolean showLogo) {

    public record Background(String color, Image image) {
    }

    public record Image(byte[] bytes, String contentType) {
    }

    /** title (IAM_TITLE), divider (IAM_SECTION, IAM_CLOSING) or content (all others). */
    public static String kindOf(String layout) {
        return "IAM_TITLE".equals(layout) ? "title" : "IAM_SECTION".equals(layout) || "IAM_CLOSING".equals(layout) ? "divider" : "content";
    }
}
