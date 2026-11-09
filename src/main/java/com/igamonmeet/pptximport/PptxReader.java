package com.igamonmeet.pptximport;

import com.igamonmeet.account.AccountException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Reads a .pptx/.potx into plain slide extracts (ADR 0005). Charts and pictures are counted, not imported. */
@Component
public class PptxReader {

    static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Set<String> MAIN_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
            "application/vnd.openxmlformats-officedocument.presentationml.template.main+xml");

    /**
     * One slide's content. {@code placeholders} holds House template placeholders by name (paragraphs);
     * {@code bodyShapes} every other text shape's paragraphs in order; {@code texts} every text shape's full text in order.
     */
    public record RawSlide(int n, String layout, String title, Map<String, List<String>> placeholders, List<List<String>> bodyShapes,
                           List<String> texts, List<List<String>> table, String notes, int pictures, int charts) {
    }

    public List<RawSlide> read(String fileName, byte[] file) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".ppt") || lower.endsWith(".pptm") || lower.endsWith(".potm")) {
            throw bad("Older .ppt and macro-enabled files are not accepted. Open the file in PowerPoint and save it as .pptx.");
        }
        if (!lower.endsWith(".pptx") && !lower.endsWith(".potx")) {
            throw bad("Upload a PowerPoint .pptx or .potx file.");
        }
        if (file == null || file.length == 0 || file.length > MAX_BYTES) {
            throw bad("The file must be between 1 byte and 50 MB.");
        }
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(file))) {
            PackagePart main = pkg.getPartsByRelationshipType(PackageRelationshipTypes.CORE_DOCUMENT).get(0);
            if (!MAIN_TYPES.contains(main.getContentType())) {
                throw bad("Macro-enabled files are not accepted. Save the file as .pptx.");
            }
            try (XMLSlideShow ppt = new XMLSlideShow(pkg)) {
                List<RawSlide> out = new ArrayList<>();
                int n = 0;
                for (XSLFSlide slide : ppt.getSlides()) {
                    out.add(slide(++n, ppt, slide));
                }
                return out;
            }
        } catch (AccountException e) {
            throw e;
        } catch (Exception e) {
            throw bad("This is not a readable PowerPoint file.");
        }
    }

    private static RawSlide slide(int n, XMLSlideShow ppt, XSLFSlide slide) {
        String layout = slide.getSlideLayout() == null ? "" : slide.getSlideLayout().getName();
        Map<String, List<String>> placeholders = new LinkedHashMap<>();
        List<List<String>> bodyShapes = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<List<String>> table = null;
        String title = null;
        int pictures = 0;
        int charts = 0;
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape t) {
                List<String> paras = paragraphs(t);
                if (paras.size() == 1 && paras.get(0).matches("Exported .* · House template v\\d+")) {
                    continue; // our own export stamp
                }
                String text = String.join("\n", paras);
                texts.add(text);
                String name = shape.getShapeName();
                boolean isTitle = "title".equals(name) || t.getTextType() == Placeholder.TITLE || t.getTextType() == Placeholder.CENTERED_TITLE;
                if (isTitle && title == null) {
                    title = text;
                } else if (t.isPlaceholder() && name != null && !name.contains(" ")) {
                    placeholders.put(name, paras);
                } else if (!paras.isEmpty()) {
                    bodyShapes.add(paras);
                }
            } else if (shape instanceof XSLFTable tbl && table == null) {
                table = new ArrayList<>();
                for (XSLFTableRow row : tbl.getRows()) {
                    List<String> cells = new ArrayList<>();
                    row.getCells().forEach(c -> cells.add(c.getText().trim()));
                    table.add(cells);
                }
            } else if (shape instanceof XSLFPictureShape) {
                pictures++;
            } else if (shape instanceof XSLFGraphicFrame g && g.hasChart()) {
                charts++;
            }
        }
        if (title == null && !texts.isEmpty()) {
            title = texts.get(0);
        }
        String notes = "";
        XSLFNotes ns = slide.getNotes();
        if (ns != null) {
            for (XSLFTextShape t : ns.getPlaceholders()) {
                if (t.getTextType() == Placeholder.BODY) {
                    notes = t.getText().trim();
                }
            }
        }
        return new RawSlide(n, layout, title == null ? "" : title.trim(), placeholders, bodyShapes, texts,
                table == null ? List.of() : table, notes, pictures, charts);
    }

    private static List<String> paragraphs(XSLFTextShape t) {
        List<String> out = new ArrayList<>();
        for (XSLFTextParagraph p : t.getTextParagraphs()) {
            String s = p.getText().trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static AccountException bad(String message) {
        return new AccountException(HttpStatus.BAD_REQUEST, "INVALID_FILE", message);
    }
}
