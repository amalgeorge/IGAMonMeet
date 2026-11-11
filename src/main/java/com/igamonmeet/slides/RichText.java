package com.igamonmeet.slides;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Formatted text in a Section (docs/spec-v3.md §2). Stored as JSON, never HTML:
 * <pre>
 * {"blocks": [
 *   {"type": "p",  "runs": [{"text": "Plain "}, {"text": "bold", "b": true}, {"text": "link", "href": "https://…"}]},
 *   {"type": "ul" | "ol", "items": [{"runs": [...], "items": [{"runs": [...]}]}]}   one level of sub-items
 * ]}
 * </pre>
 * For slides each paragraph or list item becomes one line string: a prefix says what kind of line it is, and inline
 * control characters mark bold, italic and links, so the existing line-based fitting and paging can carry formatting.
 */
public final class RichText {

    /** Line prefixes: unordered item, ordered item ("\u0002" + number + "\u0004"), sub-item (after the item prefix). */
    public static final char BULLET = '\u0001';
    public static final char NUMBER = '\u0002';
    public static final char NUMBER_END = '\u0004';
    public static final char SUB = '\u0003';
    /** Inline marks: bold and italic toggle; a link is LINK url LINK_TEXT text LINK_END. */
    public static final char BOLD = '\u0010';
    public static final char ITALIC = '\u0011';
    public static final char LINK = '\u0012';
    public static final char LINK_TEXT = '\u0013';
    public static final char LINK_END = '\u0014';

    private static final Pattern HREF = Pattern.compile("(?i)(https?://|mailto:)[^\\s\"<>]{1,1990}");
    private static final Pattern MARKUP = Pattern.compile("\u0012[^\u0013]*\u0013|[\u0001-\u0004\u0010-\u0014]|(?<=\u0002)\\d+");

    private RichText() {
    }

    public static boolean isEmpty(JsonNode rich) {
        return plain(rich).isBlank();
    }

    /** Null when {@code rich} is a valid document (or absent), else what is wrong with it. */
    public static String problem(JsonNode rich) {
        if (rich == null || rich.isNull() || rich.isMissingNode()) return null;
        if (!rich.isObject() || !rich.path("blocks").isArray()) return "Formatted text needs a list of blocks.";
        if (rich.get("blocks").size() > 300) return "Formatted text holds up to 300 paragraphs and lists.";
        for (JsonNode b : rich.get("blocks")) {
            String t = b.path("type").asText();
            if (t.equals("p")) {
                String p = runsProblem(b.path("runs"));
                if (p != null) return p;
            } else if (t.equals("ul") || t.equals("ol")) {
                if (!b.path("items").isArray() || b.get("items").size() > 200) return "A list needs 1 to 200 items.";
                for (JsonNode i : b.get("items")) {
                    String p = runsProblem(i.path("runs"));
                    if (p != null) return p;
                    if (i.has("items")) {
                        if (!i.get("items").isArray() || i.get("items").size() > 50) return "A list item holds up to 50 sub-items.";
                        for (JsonNode sub : i.get("items")) {
                            if (sub.has("items") && sub.get("items").size() > 0) return "Lists go one level deep.";
                            String q = runsProblem(sub.path("runs"));
                            if (q != null) return q;
                        }
                    }
                }
            } else {
                return "Unknown formatted-text block '" + t + "'.";
            }
        }
        return null;
    }

    private static String runsProblem(JsonNode runs) {
        if (!runs.isArray() || runs.size() > 200) return "Each paragraph needs a list of up to 200 text runs.";
        for (JsonNode r : runs) {
            if (!r.path("text").isTextual() || r.get("text").asText().length() > 5000) return "Each text run needs text of up to 5000 characters.";
            if (r.has("href") && !r.get("href").isNull() && !HREF.matcher(r.get("href").asText()).matches()) {
                return "Links must start with https://, http:// or mailto:.";
            }
            if (MARKUP.matcher(r.get("text").asText()).find()) return "Text contains control characters.";
        }
        return null;
    }

    /** One line per paragraph and list item, with prefixes and inline marks (see class comment). */
    public static List<String> lines(JsonNode rich) {
        List<String> out = new ArrayList<>();
        if (rich == null) return out;
        for (JsonNode b : rich.path("blocks")) {
            switch (b.path("type").asText()) {
                case "p" -> {
                    String l = runs(b.path("runs"));
                    if (!strip(l).isBlank()) out.add(l);
                }
                case "ul", "ol" -> {
                    boolean ordered = "ol".equals(b.path("type").asText());
                    int n = 0;
                    for (JsonNode i : b.path("items")) {
                        n++;
                        out.add((ordered ? NUMBER + Integer.toString(n) + NUMBER_END : String.valueOf(BULLET)) + runs(i.path("runs")));
                        int m = 0;
                        for (JsonNode sub : i.path("items")) {
                            m++;
                            out.add((ordered ? NUMBER + Integer.toString(m) + NUMBER_END : String.valueOf(BULLET)) + SUB + runs(sub.path("runs")));
                        }
                    }
                }
                default -> { }
            }
        }
        return out;
    }

    private static String runs(JsonNode runs) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode r : runs) {
            String t = r.path("text").asText("");
            if (t.isEmpty()) continue;
            boolean b = r.path("b").asBoolean(false);
            boolean i = r.path("i").asBoolean(false);
            String href = r.path("href").asText(null);
            if (href != null && !HREF.matcher(href).matches()) href = null;
            if (href != null) sb.append(LINK).append(href).append(LINK_TEXT);
            if (b) sb.append(BOLD);
            if (i) sb.append(ITALIC);
            sb.append(t);
            if (i) sb.append(ITALIC);
            if (b) sb.append(BOLD);
            if (href != null) sb.append(LINK_END);
        }
        return sb.toString();
    }

    /**
     * Plain text made safe to travel as a slide line: control characters removed (tab and line break kept), so text
     * typed or pasted by an editor can never be read as a list prefix, a bold/italic mark or a link.
     */
    public static String clean(String text) {
        if (text == null) return "";
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean bad = c < 0x20 && c != '\n' && c != '\t';
            if (bad && sb == null) sb = new StringBuilder(text.length()).append(text, 0, i);
            else if (!bad && sb != null) sb.append(c);
        }
        return sb == null ? text : sb.toString();
    }

    /** True when text holds a control character other than tab, line feed or carriage return. */
    public static boolean hasControlCharacters(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t' && c != '\r') return true;
        }
        return false;
    }

    /** The link address if it is one the app allows (http, https, mailto), else null. Renderers check again. */
    public static String safeHref(String href) {
        return href != null && HREF.matcher(href).matches() ? href : null;
    }

    /** The visible text of a line: prefixes, marks and link targets removed (for measuring). */
    public static String strip(String line) {
        return line == null ? "" : MARKUP.matcher(line).replaceAll("");
    }

    /** All visible text, paragraphs and items on their own lines. */
    public static String plain(JsonNode rich) {
        List<String> ls = lines(rich);
        StringBuilder sb = new StringBuilder();
        for (String l : ls) sb.append(strip(l)).append('\n');
        return sb.toString().trim();
    }
}
