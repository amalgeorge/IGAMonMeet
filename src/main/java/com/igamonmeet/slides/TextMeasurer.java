package com.igamonmeet.slides;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.text.AttributedString;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts wrapped lines the way PowerPoint will lay them out, using the template font (ADR 0004).
 * Needs Arial, or the metric-compatible Liberation Sans, installed on the server; otherwise falls back with a warning.
 */
@Component
public class TextMeasurer {

    private static final Logger LOG = LoggerFactory.getLogger(TextMeasurer.class);
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private final String family;

    public TextMeasurer() {
        Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        if (installed.contains("Arial")) {
            family = "Arial";
        } else if (installed.contains("Liberation Sans")) {
            family = "Liberation Sans";
        } else {
            family = Font.SANS_SERIF;
            LOG.warn("Neither Arial nor Liberation Sans is installed; slide text fitting will be approximate.");
        }
    }

    public String family() {
        return family;
    }

    /** Lines the text needs in a box {@code widthPt} wide at {@code sizePt}. Empty text takes one line. */
    public int lines(String text, float sizePt, boolean bold, double widthPt) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        Font font = new Font(family, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(sizePt);
        int total = 0;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                total++;
                continue;
            }
            AttributedString as = new AttributedString(para);
            as.addAttribute(TextAttribute.FONT, font);
            LineBreakMeasurer m = new LineBreakMeasurer(as.getIterator(), FRC);
            int n = 0;
            while (m.getPosition() < para.length()) {
                m.nextLayout((float) Math.max(widthPt, 1));
                n++;
            }
            total += Math.max(n, 1);
        }
        return total;
    }

    /** Height in points for those lines at 1.2 line spacing. */
    public double height(String text, float sizePt, boolean bold, double widthPt) {
        return lines(text, sizePt, bold, widthPt) * sizePt * 1.2;
    }
}
