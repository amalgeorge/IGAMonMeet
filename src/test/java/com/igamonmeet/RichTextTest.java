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
import com.igamonmeet.slides.RichText;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Formatted text in every Section (docs/spec-v3.md §2). */
@SpringBootTest
@AutoConfigureMockMvc
class RichTextTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String DOC = "{\"blocks\":["
            + "{\"type\":\"p\",\"runs\":[{\"text\":\"Steady \"},{\"text\":\"month\",\"b\":true},{\"text\":\" see \"},{\"text\":\"here\",\"href\":\"https://example.com\"}]},"
            + "{\"type\":\"ol\",\"items\":[{\"runs\":[{\"text\":\"First\",\"i\":true}],\"items\":[{\"runs\":[{\"text\":\"Sub\"}]}]},{\"runs\":[{\"text\":\"Second\"}]}]}]}";

    @Test
    void documentsBecomeMarkedLines() throws Exception {
        JsonNode doc = json.readTree(DOC);
        assertThat(RichText.problem(doc)).isNull();
        List<String> lines = RichText.lines(doc);
        assertThat(lines).hasSize(4);
        assertThat(RichText.strip(lines.get(0))).isEqualTo("Steady month see here");
        assertThat(lines.get(0)).contains("\u0010month\u0010").contains("\u0012https://example.com\u0013here\u0014");
        assertThat(lines.get(1)).startsWith("\u00021\u0004\u0011First");
        assertThat(lines.get(2)).startsWith("\u00021\u0004\u0003Sub");
        assertThat(RichText.strip(lines.get(3))).isEqualTo("Second");
        assertThat(RichText.problem(json.readTree("{\"blocks\":[{\"type\":\"p\",\"runs\":[{\"text\":\"x\",\"href\":\"javascript:alert(1)\"}]}]}")))
                .contains("Links must start");
        assertThat(RichText.problem(json.readTree("{\"blocks\":[{\"type\":\"h1\"}]}"))).contains("Unknown");
    }

    private long meeting(String month, String date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("rt-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions save(long m, String key, String content) throws Exception {
        JsonNode detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        for (JsonNode s : detail.get("sections")) {
            if (s.get("key").asText().equals(key)) {
                return mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + s.get("version").asLong() + ",\"content\":" + content + "}"));
            }
        }
        throw new AssertionError(key);
    }

    @Test
    void kpiCommentaryBecomesFormattedTextOnItsOwnSlide() throws Exception {
        long m = meeting("2034-08", "2034-08-17");
        save(m, "kpis", "{\"values\":{},\"rich\":{\"blocks\":[{\"type\":\"p\",\"runs\":[{\"text\":\"x\",\"href\":\"javascript:x\"}]}]}}")
                .andExpect(status().isBadRequest());
        save(m, "kpis", "{\"values\":{\"sla\":{\"value\":96},\"provisioning\":{\"value\":3},\"leaver\":{\"value\":99.5}},\"commentary\":\"\",\"rich\":" + DOC + "}")
                .andExpect(status().isOk());
        JsonNode slides = json.readTree(mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString()).get("slides");
        JsonNode text = null;
        for (JsonNode s : slides) {
            if ("kpis".equals(s.path("sectionKey").asText()) && "IAM_SUMMARY".equals(s.path("layout").asText())) text = s;
        }
        assertThat(text).as("formatted text continues on a text slide").isNotNull();
        JsonNode blocks = text.at("/data/blocks");
        assertThat(blocks.get(0).get("type").asText()).isEqualTo("p");
        assertThat(blocks.get(1).get("type").asText()).isEqualTo("oli");
        assertThat(blocks.get(2).get("level").asInt()).isEqualTo(1);

        byte[] pptx = mvc.perform(post("/api/meetings/" + m + "/export/pptx").with(csrf()).with(user("r").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sections\":[\"kpis\"],\"notes\":false,\"comments\":false,\"versionStamp\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(new java.io.ByteArrayInputStream(pptx))) {
            boolean bold = false;
            boolean link = false;
            for (var slide : ppt.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (!(shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape t)) continue;
                    for (var p : t.getTextParagraphs()) {
                        for (var r : p.getTextRuns()) {
                            bold |= "month".equals(r.getRawText()) && r.isBold();
                            link |= "here".equals(r.getRawText()) && r.getHyperlink() != null;
                        }
                    }
                }
            }
            assertThat(bold).as("bold run in PPTX").isTrue();
            assertThat(link).as("hyperlink in PPTX").isTrue();
        }
    }

    @Test
    void aTextSectionWithOnlyFormattedTextCountsAsFilled() throws Exception {
        assertThat(RichText.isEmpty(json.readTree("{\"blocks\":[{\"type\":\"p\",\"runs\":[{\"text\":\"  \"}]}]}"))).isTrue();
        assertThat(RichText.isEmpty(json.readTree(DOC))).isFalse();
    }
}
