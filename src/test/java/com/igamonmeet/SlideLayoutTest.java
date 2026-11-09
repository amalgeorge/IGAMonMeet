package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Admin's slide layout per Section (docs/spec-v3.md §6). */
@SpringBootTest
@AutoConfigureMockMvc
class SlideLayoutTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "layout test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private ResultActions publish(ObjectNode draft) throws Exception {
        return mvc.perform(post("/api/admin/templates").with(csrf()).with(user("sl-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)));
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        throw new AssertionError(key);
    }

    private static ObjectNode box(ObjectNode parent, String name, int x, int y, int w, int h) {
        return parent.putObject(name).put("x", x).put("y", y).put("w", w).put("h", h);
    }

    @Test
    void layoutsAreValidatedAndMoveTheSlideContent() throws Exception {
        ObjectNode original = currentAsDraft();

        ObjectNode bad = currentAsDraft();
        box(section(bad, "roadmap").putObject("slideLayout"), "content", 8, 2, 6, 4); // runs off the grid
        publish(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("12 x 8 grid")));
        ObjectNode wrongItem = currentAsDraft();
        section(wrongItem, "roadmap").putObject("slideLayout").putArray("tiles");
        publish(wrongItem).andExpect(status().isBadRequest());

        ObjectNode ok = currentAsDraft();
        ObjectNode layout = section(ok, "roadmap").putObject("slideLayout");
        box(layout, "title", 0, 0, 12, 1);
        box(layout, "content", 0, 2, 6, 5);
        publish(ok).andExpect(status().isCreated());
        try {
            long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("sl-admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2061-05\",\"meetingDate\":\"2061-05-19\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            JsonNode detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
            for (JsonNode s : detail.get("sections")) {
                if (s.get("key").asText().equals("roadmap")) {
                    mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + s.get("version").asLong() + ",\"content\":{\"rows\":[[\"SSO\",\"IAM\",\"On track\",\"Dec\"]]}}"))
                            .andExpect(status().isOk());
                }
            }
            JsonNode slides = json.readTree(mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                    .andReturn().getResponse().getContentAsString()).get("slides");
            JsonNode roadmap = null;
            for (JsonNode s : slides) if ("roadmap".equals(s.path("sectionKey").asText())) roadmap = s;
            assertThat(roadmap.at("/data/frame/content/w").asDouble()).isEqualTo(50.0);
            assertThat(roadmap.at("/data/frame/content/y").asDouble()).isEqualTo(25.0);

            byte[] pptx = mvc.perform(post("/api/meetings/" + m + "/export/pptx").with(csrf()).with(user("r").roles("END_USER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"sections\":[\"roadmap\"],\"notes\":false,\"comments\":false,\"versionStamp\":false}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(pptx))) {
                double w = ppt.getPageSize().getWidth();
                double h = ppt.getPageSize().getHeight();
                XSLFTable t = ppt.getSlides().stream().flatMap(sl -> sl.getShapes().stream()).filter(x -> x instanceof XSLFTable)
                        .map(x -> (XSLFTable) x).findFirst().orElseThrow();
                assertThat(t.getAnchor().getX()).isLessThan(1.0);
                assertThat(t.getAnchor().getWidth()).isCloseTo(w / 2, org.assertj.core.data.Offset.offset(2.0));
                assertThat(t.getAnchor().getY()).isCloseTo(h * 0.25, org.assertj.core.data.Offset.offset(2.0));
            }
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore).andExpect(status().isCreated());
        }
    }
}
