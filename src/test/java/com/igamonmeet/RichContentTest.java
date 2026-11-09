package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RichContentTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "rich content test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private void publish(ObjectNode draft) throws Exception {
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("rc-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft))).andExpect(status().isCreated());
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        }
        throw new AssertionError(key);
    }

    private long meeting(String month, LocalDate date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("rc-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode detail(long m) throws Exception {
        return json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
    }

    private List<String> keys(JsonNode detail) {
        List<String> k = new ArrayList<>();
        detail.get("sections").forEach(s -> k.add(s.get("key").asText()));
        return k;
    }

    private void fill(long m, String key, String content) throws Exception {
        for (JsonNode s : detail(m).get("sections")) {
            if (s.get("key").asText().equals(key)) {
                mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + s.get("version").asLong() + ",\"content\":" + content + "}"))
                        .andExpect(status().isOk());
            }
        }
    }

    private org.springframework.test.web.servlet.ResultActions save(long m, String key, String content) throws Exception {
        for (JsonNode s : detail(m).get("sections")) {
            if (s.get("key").asText().equals(key)) {
                return mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + s.get("version").asLong() + ",\"content\":" + content + "}"));
            }
        }
        throw new AssertionError(key);
    }

    @Test
    void textSectionsHoldParagraphAndBulletBlocks() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        ObjectNode sec = ((ArrayNode) draft.get("sections")).insertObject(1);
        sec.put("key", "rc-text").put("name", "Highlights").put("teamTag", "SHARED").put("kind", "TEXT")
                .put("layout", "IAM_SUMMARY").put("overflowRule", "SHRINK_TO_FIT").put("required", false);
        sec.putArray("columns");
        sec.putArray("kpis");
        publish(draft);
        try {
            long m = meeting("2050-07", LocalDate.of(2050, 7, 28));
            save(m, "rc-text", "{\"blocks\":[{\"type\":\"video\"}]}").andExpect(status().isBadRequest());
            save(m, "rc-text", "{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"Quiet month for access requests.\"},"
                    + "{\"type\":\"bullets\",\"items\":[\"SSO wave 3 done\",\"PAM pilot started\"]}]}").andExpect(status().isOk());

            mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='rc-text')].filled").value(true));
            mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.slides[?(@.sectionKey=='rc-text')].layout").value("IAM_SUMMARY"))
                    .andExpect(jsonPath("$.slides[?(@.sectionKey=='rc-text')].data.blocks[0].type").value("p"))
                    .andExpect(jsonPath("$.slides[?(@.sectionKey=='rc-text')].data.blocks[1].type").value("li"))
                    .andExpect(jsonPath("$.slides[?(@.sectionKey=='rc-text')].data.blocks[2].text").value("PAM pilot started"));

            byte[] pptx = mvc.perform(post("/api/meetings/" + m + "/export/pptx").with(csrf()).with(user("r").roles("END_USER"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"sections\":[\"rc-text\"],\"notes\":false,\"comments\":false,\"versionStamp\":false}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(new java.io.ByteArrayInputStream(pptx))) {
                String all = ppt.getSlides().stream().flatMap(sl -> sl.getShapes().stream())
                        .filter(sh -> sh instanceof org.apache.poi.xslf.usermodel.XSLFTextShape)
                        .map(sh -> ((org.apache.poi.xslf.usermodel.XSLFTextShape) sh).getText()).reduce("", (a, b) -> a + "\n" + b);
                org.assertj.core.api.Assertions.assertThat(all).contains("Quiet month for access requests.").contains("SSO wave 3 done");
            }
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore);
        }
    }
}
