package com.igamonmeet.house;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.account.AccountException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTheme;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Checks an uploaded House template against the contract and extracts the theme the browser draws slides with. */
@Component
public class TemplateInspector {

    static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final Set<String> MAIN_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
            "application/vnd.openxmlformats-officedocument.presentationml.template.main+xml");

    private final ObjectMapper json;

    public TemplateInspector(ObjectMapper json) {
        this.json = json;
    }

    /** Returns the theme JSON, or throws 400 listing every problem found. */
    public String inspect(byte[] file) {
        if (file == null || file.length == 0) {
            throw bad(List.of("The file is empty."));
        }
        if (file.length > MAX_BYTES) {
            throw bad(List.of("The file is larger than 50 MB."));
        }
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(file))) {
            PackagePart main = pkg.getPartsByRelationshipType(PackageRelationshipTypes.CORE_DOCUMENT).get(0);
            if (!MAIN_TYPES.contains(main.getContentType())) {
                throw bad(List.of("Only .pptx or .potx files without macros are accepted. Re-save the file as .pptx."));
            }
            try (XMLSlideShow ppt = new XMLSlideShow(pkg)) {
                Map<String, XSLFSlideLayout> layouts = new HashMap<>();
                for (XSLFSlideMaster master : ppt.getSlideMasters()) {
                    for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                        layouts.putIfAbsent(layout.getName(), layout);
                    }
                }
                List<String> problems = new ArrayList<>();
                HouseLayouts.REQUIRED.forEach((name, placeholders) -> {
                    XSLFSlideLayout layout = layouts.get(name);
                    if (layout == null) {
                        problems.add("Layout " + name + " is missing.");
                        return;
                    }
                    Set<String> present = new HashSet<>();
                    layout.getShapes().forEach(s -> present.add(s.getShapeName()));
                    placeholders.stream().filter(p -> !present.contains(p))
                            .forEach(p -> problems.add("Layout " + name + " has no placeholder named '" + p + "'."));
                });
                if (!problems.isEmpty()) {
                    throw bad(problems);
                }
                return theme(ppt, layouts);
            }
        } catch (AccountException e) {
            throw e;
        } catch (Exception e) {
            throw bad(List.of("This is not a readable PowerPoint file."));
        }
    }

    private String theme(XMLSlideShow ppt, Map<String, XSLFSlideLayout> layouts) throws Exception {
        Dimension page = ppt.getPageSize();
        ObjectNode root = json.createObjectNode();
        root.put("slideWidth", page.getWidth());
        root.put("slideHeight", page.getHeight());

        XSLFTheme theme = ppt.getSlideMasters().get(0).getTheme();
        root.put("headFont", theme.getMajorFont());
        root.put("bodyFont", theme.getMinorFont());
        ObjectNode colors = root.putObject("colors");
        // Read the colour scheme from the theme XML (avoids schema classes missing from poi-ooxml-lite).
        String xml = theme.getXmlObject().xmlText();
        for (String k : List.of("dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6")) {
            Matcher m = Pattern.compile("<(?:\\w+:)?" + k + ">\\s*<(?:\\w+:)?(?:srgbClr val|sysClr[^>]*lastClr)=\"([0-9A-Fa-f]{6})\"").matcher(xml);
            if (m.find()) {
                colors.put(k, m.group(1).toUpperCase());
            }
        }

        ObjectNode out = root.putObject("layouts");
        for (Map.Entry<String, List<String>> e : HouseLayouts.REQUIRED.entrySet()) {
            XSLFSlideLayout layout = layouts.get(e.getKey());
            ObjectNode l = out.putObject(e.getKey());
            Color bg = layout.getBackground() == null ? null : layout.getBackground().getFillColor();
            if (bg != null) {
                l.put("background", String.format("%02X%02X%02X", bg.getRed(), bg.getGreen(), bg.getBlue()));
            }
            ObjectNode ph = l.putObject("placeholders");
            for (XSLFShape s : layout.getShapes()) {
                if (e.getValue().contains(s.getShapeName())) {
                    Rectangle2D a = s.getAnchor();
                    ObjectNode box = ph.putObject(s.getShapeName());
                    box.put("x", pct(a.getX(), page.getWidth()));
                    box.put("y", pct(a.getY(), page.getHeight()));
                    box.put("w", pct(a.getWidth(), page.getWidth()));
                    box.put("h", pct(a.getHeight(), page.getHeight()));
                }
            }
        }
        return json.writeValueAsString(root);
    }

    private static double pct(double v, double total) {
        return Math.round(v / total * 10000) / 100.0;
    }

    private static AccountException bad(List<String> problems) {
        return new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE", String.join(" ", problems));
    }
}
