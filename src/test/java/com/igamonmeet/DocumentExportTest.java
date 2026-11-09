package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** JSON export for Word conversion (docs/json-export.md). */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentExportTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long meeting() throws Exception {
        long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("dx-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2033-09\",\"meetingDate\":\"2033-09-15\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        for (JsonNode s : detail.get("sections")) {
            String content = switch (s.get("key").asText()) {
                case "summary" -> "{\"points\":[\"Leaver removal above 99%\"],\"stats\":[{\"value\":\"12\",\"label\":\"Apps onboarded\"}],\"notes\":\"Start with the good news.\"}";
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

    private JsonNode export(long m, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/meetings/" + m + "/export/json").with(csrf()).with(user("r").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".json")))
                .andReturn().getResponse().getContentAsString());
    }

    private static JsonNode section(JsonNode doc, String key) {
        for (JsonNode s : doc.get("sections")) {
            if (key.equals(s.get("key").asText())) return s;
        }
        return null;
    }

    @Test
    void exportsTheMeetingAsDocumentShapedBlocks() throws Exception {
        long m = meeting();
        JsonNode doc = export(m, "{\"sections\":null,\"notes\":true,\"comments\":true,\"versionStamp\":false}");

        assertThat(doc.get("schema").asText()).isEqualTo("igamonmeet.document/1");
        assertThat(doc.at("/meeting/monthText").asText()).isEqualTo("September 2033");
        assertThat(doc.at("/meeting/date").asText()).isEqualTo("2033-09-15");
        assertThat(doc.at("/meeting/theme/name").asText()).isNotBlank();
        assertThat(doc.get("exportedBy").asText()).isEqualTo("r");

        JsonNode summary = section(doc, "summary");
        assertThat(summary.at("/blocks/0/type").asText()).isEqualTo("stats");
        assertThat(summary.at("/blocks/0/items/0/label").asText()).isEqualTo("Apps onboarded");
        assertThat(summary.at("/blocks/1/type").asText()).isEqualTo("bullets");
        assertThat(summary.at("/blocks/1/items/0").asText()).isEqualTo("Leaver removal above 99%");
        assertThat(summary.get("notes").asText()).isEqualTo("Start with the good news.");

        JsonNode kpis = section(doc, "kpis");
        assertThat(kpis.at("/blocks/0/type").asText()).isEqualTo("kpis");
        List<String> names = new ArrayList<>();
        kpis.at("/blocks/0/items").forEach(k -> names.add(k.get("key").asText()));
        assertThat(names).contains("sla", "provisioning", "leaver");
        JsonNode sla = kpis.at("/blocks/0/items/0");
        assertThat(sla.get("value").decimalValue()).isEqualByComparingTo("96.4");
        assertThat(sla.get("status").asText()).isEqualTo("ON_TARGET");
        assertThat(sla.get("display").asText()).isNotBlank();
        assertThat(kpis.at("/blocks/1/text").asText()).isEqualTo("Steady.");
        assertThat(kpis.at("/comments/0/body").asText()).isEqualTo("Name the BUs.");

        JsonNode roadmap = section(doc, "roadmap");
        assertThat(roadmap.at("/blocks/0/type").asText()).isEqualTo("table");
        assertThat(roadmap.at("/blocks/0/columns/0").asText()).isEqualTo("Initiative");
        assertThat(roadmap.at("/blocks/0/rows/0/0").asText()).isEqualTo("SSO wave 3");
        assertThat(roadmap.at("/blocks/0/style").isObject()).isTrue();

        assertThat(section(doc, "actions").at("/blocks/0/type").asText()).isEqualTo("actionItems");
        assertThat(doc.get("actionItems").isArray()).isTrue();
    }

    @Test
    void sectionsNotesAndCommentsFollowTheOptions() throws Exception {
        long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("dx-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2033-10\",\"meetingDate\":\"2033-10-13\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode doc = export(m, "{\"sections\":[\"summary\"],\"notes\":false,\"comments\":false,\"versionStamp\":false}");
        assertThat(doc.get("sections")).hasSize(1);
        assertThat(doc.at("/sections/0/key").asText()).isEqualTo("summary");
        assertThat(doc.at("/sections/0").has("notes")).isFalse();
        assertThat(doc.at("/sections/0").has("comments")).isFalse();
    }
}
