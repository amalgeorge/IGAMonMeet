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

    @Test
    void tableBlocksGetTheirOwnSlidesWithTheHeaderRepeated() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        ObjectNode sec = ((ArrayNode) draft.get("sections")).insertObject(1);
        sec.put("key", "rc-tables").put("name", "Vendor status").put("teamTag", "SHARED").put("kind", "TEXT")
                .put("layout", "IAM_SUMMARY").put("overflowRule", "CONTINUE").put("required", false);
        sec.putArray("columns");
        sec.putArray("kpis");
        publish(draft);
        try {
            long m = meeting("2050-08", LocalDate.of(2050, 8, 27));
            save(m, "rc-tables", "{\"blocks\":[{\"type\":\"table\",\"columns\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\",\"l\",\"m\"],\"rows\":[]}]}")
                    .andExpect(status().isBadRequest());
            save(m, "rc-tables", "{\"blocks\":[{\"type\":\"table\",\"columns\":[\"Vendor\",\"Status\"],\"rows\":[[\"Only one cell\"]]}]}")
                    .andExpect(status().isBadRequest());

            StringBuilder rows = new StringBuilder();
            for (int i = 0; i < 40; i++) rows.append(i == 0 ? "" : ",").append("[\"Vendor ").append(i).append("\",\"On track\"]");
            save(m, "rc-tables", "{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"Before the table.\"},"
                    + "{\"type\":\"table\",\"columns\":[\"Vendor\",\"Status\"],\"rows\":[" + rows + "]},"
                    + "{\"type\":\"paragraph\",\"text\":\"After the table.\"}]}").andExpect(status().isOk());

            JsonNode plan = json.readTree(mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                    .andReturn().getResponse().getContentAsString());
            List<String> layouts = new java.util.ArrayList<>();
            int tableRows = 0;
            for (JsonNode sl : plan.get("slides")) {
                if (!"rc-tables".equals(sl.path("sectionKey").asText())) continue;
                layouts.add(sl.get("layout").asText());
                if ("IAM_TABLE".equals(sl.get("layout").asText())) {
                    org.assertj.core.api.Assertions.assertThat(sl.at("/data/head/0").asText()).isEqualTo("Vendor");
                    tableRows += sl.at("/data/rows").size();
                }
            }
            org.assertj.core.api.Assertions.assertThat(layouts.get(0)).isEqualTo("IAM_SUMMARY");
            org.assertj.core.api.Assertions.assertThat(layouts.get(layouts.size() - 1)).isEqualTo("IAM_SUMMARY");
            org.assertj.core.api.Assertions.assertThat(layouts.stream().filter("IAM_TABLE"::equals).count()).isGreaterThan(1);
            org.assertj.core.api.Assertions.assertThat(tableRows).isEqualTo(40);
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore);
        }
    }

    @Test
    void tableStylesMustFitTheirColumns() throws Exception {
        ObjectNode bad = currentAsDraft();
        ObjectNode incidents = section(bad, "sitrep");
        incidents.putObject("tableStyle").putArray("widths").add(1).add(2);
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("rc-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("one column width per column")));

        ObjectNode ok = currentAsDraft();
        ObjectNode style = section(ok, "sitrep").putObject("tableStyle");
        style.put("statusColumn", 2).put("banded", true);
        ObjectNode original = currentAsDraft();
        publish(ok);
        try {
            mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='sitrep')].tableStyle.statusColumn").value(2));
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore);
        }
    }
}
