package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ExportTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long meetingWithContent(String month, String date) throws Exception {
        String created = mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("ex-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}")).andReturn().getResponse().getContentAsString();
        long m = json.readTree(created).get("id").asLong();
        JsonNode detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        for (JsonNode s : detail.get("sections")) {
            String content = switch (s.get("key").asText()) {
                case "summary" -> "{\"points\":[\"Leaver removal above 99%\"],\"stats\":[],\"notes\":\"Start with the good news.\"}";
                case "kpis" -> "{\"values\":{\"sla\":{\"value\":96.4},\"provisioning\":{\"value\":3.2},\"leaver\":{\"value\":99.1}},\"commentary\":\"Steady.\"}";
                case "roadmap" -> "{\"rows\":[[\"SSO wave 3\",\"IAM Engineering\",\"On track\",\"Dec\"]]}";
                default -> null;
            };
            if (content != null) {
                mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"content\":" + content + "}")).andExpect(status().isOk());
            }
        }
        mvc.perform(post("/api/meetings/" + m + "/comments").with(csrf()).with(user("chair").roles("END_USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionKey\":\"kpis\",\"body\":\"Name the BUs.\"}")).andExpect(status().isCreated());
        return m;
    }

    private XMLSlideShow export(long m, String body) throws Exception {
        byte[] bytes = mvc.perform(post("/api/meetings/" + m + "/export/pptx").with(csrf()).with(user("r").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new XMLSlideShow(new ByteArrayInputStream(bytes));
    }

    /** Text of every text shape and table cell on the slide. */
    private static String texts(XSLFSlide s) {
        StringBuilder out = new StringBuilder();
        for (XSLFShape x : s.getShapes()) {
            if (x instanceof XSLFTextShape t) {
                out.append(t.getText()).append(" | ");
            } else if (x instanceof XSLFTable t) {
                t.getRows().forEach(r -> r.getCells().forEach(c -> out.append(c.getText()).append(" | ")));
            }
        }
        return out.toString();
    }

    @Test
    void exportUsesHouseLayoutsAndMatchesThePlan() throws Exception {
        long m = meetingWithContent("2039-04", "2039-04-28");
        int planned = json.readTree(mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString()).get("slides").size();
        try (XMLSlideShow ppt = export(m, "{\"sections\":null,\"notes\":true,\"comments\":true,\"versionStamp\":true}")) {
            List<XSLFSlide> slides = ppt.getSlides();
            assertThat(slides).hasSize(planned + 1); // + comments appendix
            assertThat(slides.get(0).getSlideLayout().getName()).isEqualTo("IAM_TITLE");
            assertThat(texts(slides.get(0))).contains("April 2039");

            XSLFSlide summary = slides.stream().filter(s -> s.getSlideLayout().getName().equals("IAM_SUMMARY")).findFirst().orElseThrow();
            assertThat(texts(summary)).contains("Leaver removal above 99%").contains("House template v");
            XSLFNotes notes = ppt.getNotesSlide(summary);
            assertThat(notes.getShapes().stream().filter(x -> x instanceof XSLFTextShape).map(x -> ((XSLFTextShape) x).getText()).toList())
                    .anyMatch(t -> t.contains("Start with the good news."));

            XSLFSlide kpi = slides.stream().filter(s -> s.getSlideLayout().getName().equals("IAM_KPI")).findFirst().orElseThrow();
            assertThat(texts(kpi)).contains("96.4%").contains("On target");

            XSLFSlide table = slides.stream().filter(s -> s.getSlideLayout().getName().equals("IAM_TABLE") && texts(s).contains("Roadmap")).findFirst().orElseThrow();
            XSLFTable t = (XSLFTable) table.getShapes().stream().filter(x -> x instanceof XSLFTable).findFirst().orElseThrow();
            assertThat(t.getCell(0, 0).getText()).isEqualTo("Initiative");
            assertThat(t.getCell(1, 0).getText()).isEqualTo("SSO wave 3");

            assertThat(texts(slides.get(slides.size() - 1))).contains("Comments").contains("Name the BUs.");
        }
    }

    @Test
    void exportCanBeLimitedToSomeSections() throws Exception {
        long m = meetingWithContent("2039-05", "2039-05-26");
        try (XMLSlideShow ppt = export(m, "{\"sections\":[\"summary\"],\"notes\":false,\"comments\":false,\"versionStamp\":false}")) {
            // Title, agenda (spec-v3 §5), the Summary Section, closing.
            assertThat(ppt.getSlides()).extracting(s -> s.getSlideLayout().getName()).containsExactly("IAM_TITLE", "IAM_SUMMARY", "IAM_SUMMARY", "IAM_CLOSING");
            for (XSLFShape shape : ppt.getSlides().get(2).getShapes()) {
                assertThat(shape).isNotInstanceOf(XSLFGraphicFrame.class);
            }
        }
    }
}
