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
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Agenda slide and links between slides (docs/spec-v3.md §5). */
@SpringBootTest
@AutoConfigureMockMvc
class AgendaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long meeting() throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("ag-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2035-01\",\"meetingDate\":\"2035-01-18\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode slides(long m) throws Exception {
        return json.readTree(mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString()).get("slides");
    }

    @Test
    void agendaLinksEverySectionAndEveryContentSlideLinksBack() throws Exception {
        long m = meeting();
        JsonNode slides = slides(m);
        JsonNode agenda = slides.get(1);
        assertThat(agenda.at("/data/title").asText()).startsWith("Agenda");
        int agendaN = agenda.get("n").asInt();
        for (JsonNode e : agenda.at("/data/agenda")) {
            int target = e.path("target").asInt(0);
            assertThat(target).as("entry %s has a target", e.get("text").asText()).isGreaterThan(agendaN);
            JsonNode t = slides.get(target - 1);
            assertThat(t.path("sectionKey").asText(null) != null || "IAM_SECTION".equals(t.get("layout").asText())).isTrue();
        }
        for (JsonNode s : slides) {
            if (!s.path("sectionKey").isNull() && s.has("sectionKey") && !s.path("sectionKey").asText().isEmpty()) {
                assertThat(s.at("/data/agendaTarget").asInt()).isEqualTo(agendaN);
            }
            if ("IAM_SECTION".equals(s.get("layout").asText())) {
                assertThat(s.at("/data/links").size()).isGreaterThan(0);
            }
        }

        byte[] pptx = mvc.perform(post("/api/meetings/" + m + "/export/pptx").with(csrf()).with(user("r").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sections\":null,\"notes\":false,\"comments\":false,\"versionStamp\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(pptx))) {
            int links = 0;
            for (var shape : ppt.getSlides().get(1).getShapes()) {
                if (!(shape instanceof XSLFTextShape t)) continue;
                for (var p : t.getTextParagraphs()) for (var r : p.getTextRuns()) if (r.getHyperlink() != null) links++;
            }
            assertThat(links).as("agenda entries link to slides in PowerPoint").isGreaterThan(0);
        }
    }

    @Test
    void adminCanSwitchTheAgendaOff() throws Exception {
        mvc.perform(put("/api/admin/settings/AGENDA_SLIDE").with(csrf()).with(user("ag-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isNoContent());
        try {
            JsonNode slides = slides(meeting2());
            for (JsonNode s : slides) {
                assertThat(s.at("/data/title").asText()).doesNotStartWith("Agenda");
                assertThat(s.at("/data/agendaTarget").isMissingNode()).isTrue();
            }
        } finally {
            mvc.perform(put("/api/admin/settings/AGENDA_SLIDE").with(csrf()).with(user("ag-admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isNoContent());
        }
    }

    private long meeting2() throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("ag-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2035-02\",\"meetingDate\":\"2035-02-15\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
