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
class KpiLibraryTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "kpi library test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private void publish(ObjectNode draft) throws Exception {
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("lib-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft))).andExpect(status().isCreated());
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        }
        throw new AssertionError(key);
    }

    private long meeting(String month, LocalDate date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("lib-admin").roles("ADMIN"))
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

    @Test
    void existingKpisAreInTheLibrary() throws Exception {
        mvc.perform(get("/api/templates/1").with(user("r").roles("END_USER")))
                .andExpect(jsonPath("$.kpiLibrary.length()").value(4))
                .andExpect(jsonPath("$.kpiLibrary[?(@.key=='sla')].name").value("Access requests within SLA"));
    }

    @Test
    void oneKpiPlacedInTwoSectionsKeepsOneHistory() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        JsonNode lib = json.readTree(mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString()).get("kpiLibrary");
        ArrayNode library = ((ArrayNode) lib).deepCopy();
        for (JsonNode k : library) {
            if (k.get("key").asText().equals("sla")) ((ObjectNode) k).put("name", "SLA met");
        }
        draft.set("kpiLibrary", library);
        ObjectNode slaPlacement = null;
        for (JsonNode k : section(draft, "kpis").get("kpis")) {
            if (k.get("key").asText().equals("sla")) slaPlacement = ((ObjectNode) k).deepCopy();
        }
        ObjectNode eng = ((ArrayNode) draft.get("sections")).addObject();
        eng.put("key", "eng-kpis").put("name", "Engineering metrics").put("teamTag", "ENGINEERING").put("kind", "KPI")
                .put("layout", "IAM_KPI").put("overflowRule", "CONTINUE").put("required", false);
        eng.putArray("columns");
        eng.putArray("kpis").add(slaPlacement);
        publish(draft);
        try {
            mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='kpis')].kpis[?(@.key=='sla')].name").value("SLA met"))
                    .andExpect(jsonPath("$.sections[?(@.key=='eng-kpis')].kpis[0].name").value("SLA met"));

            long jan = meeting("2047-01", LocalDate.of(2047, 1, 28));
            fill(jan, "kpis", "{\"values\":{\"sla\":{\"value\":90}}}");
            long feb = meeting("2047-02", LocalDate.of(2047, 2, 25));
            fill(feb, "eng-kpis", "{\"values\":{\"sla\":{\"value\":95}}}");

            mvc.perform(get("/api/meetings/" + feb).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='eng-kpis')].kpis[0].previousValue").value(90));
            mvc.perform(get("/api/meetings/" + feb + "/dashboard").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.kpis[?(@.section=='Engineering metrics')].trend.values[-2]").value(90))
                    .andExpect(jsonPath("$.kpis[?(@.section=='Engineering metrics')].trend.values[-1]").value(95));
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore);
        }
    }

    @Test
    void libraryKpiWithAmberOnTheWrongSideIsRejected() throws Exception {
        ObjectNode draft = currentAsDraft();
        ObjectNode bad = draft.putArray("kpiLibrary").addObject();
        bad.put("key", "bad").put("name", "Bad").put("unit", "%").put("target", 90).put("direction", "HIGHER").put("amberThreshold", 95);
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("lib-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amber")));
    }
}
