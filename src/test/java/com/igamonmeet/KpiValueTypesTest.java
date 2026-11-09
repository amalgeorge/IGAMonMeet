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
class KpiValueTypesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "value types test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private void publish(ObjectNode draft) throws Exception {
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("vt-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft))).andExpect(status().isCreated());
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        }
        throw new AssertionError(key);
    }

    private long meeting(String month, LocalDate date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("vt-admin").roles("ADMIN"))
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

    private ObjectNode libKpi(ArrayNode lib, String key, String type, String direction, double target, double amber) {
        ObjectNode k = lib.addObject();
        k.put("key", key).put("name", key.toUpperCase()).put("unit", "").put("target", target).put("direction", direction)
                .put("amberThreshold", amber).put("valueType", type);
        return k;
    }

    private ObjectNode placement(String key) {
        ObjectNode p = json.createObjectNode();
        p.put("key", key).put("name", key).put("unit", "").put("target", 0).put("direction", "HIGHER").put("amberThreshold", 0).put("required", false);
        return p;
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
    void eachValueTypeIsCheckedFormattedAndTracked() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        ArrayNode lib = ((ArrayNode) json.readTree(mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString()).get("kpiLibrary")).deepCopy();
        libKpi(lib, "vt-mttr", "DURATION", "LOWER", 240, 300);
        libKpi(lib, "vt-cost", "CURRENCY", "LOWER", 2000, 2500).put("currency", "USD");
        libKpi(lib, "vt-tickets", "COUNT", "LOWER", 20, 30);
        ArrayNode opts = libKpi(lib, "vt-health", "TEXT_STATUS", "HIGHER", 0, 0).putArray("statusOptions");
        opts.addObject().put("label", "Green").put("status", "ON_TARGET");
        opts.addObject().put("label", "Amber").put("status", "BELOW_TARGET");
        opts.addObject().put("label", "Red").put("status", "OFF_TARGET");
        draft.set("kpiLibrary", lib);
        ObjectNode sec = ((ArrayNode) draft.get("sections")).addObject();
        sec.put("key", "vt-kpis").put("name", "Typed metrics").put("teamTag", "OPERATIONS").put("kind", "KPI")
                .put("layout", "IAM_KPI").put("overflowRule", "CONTINUE").put("required", false);
        sec.putArray("columns");
        sec.putArray("kpis").add(placement("vt-mttr")).add(placement("vt-cost")).add(placement("vt-tickets")).add(placement("vt-health"));
        publish(draft);
        try {
            long m = meeting("2048-05", LocalDate.of(2048, 5, 30));
            save(m, "vt-kpis", "{\"values\":{\"vt-health\":{\"value\":\"Blue\"}}}").andExpect(status().isBadRequest());
            save(m, "vt-kpis", "{\"values\":{\"vt-tickets\":{\"value\":1.5}}}").andExpect(status().isBadRequest());
            save(m, "vt-kpis", "{\"values\":{\"vt-mttr\":{\"value\":150},\"vt-cost\":{\"value\":1234.5},"
                    + "\"vt-tickets\":{\"value\":12},\"vt-health\":{\"value\":\"Amber\"}}}").andExpect(status().isOk());

            String base = "$.sections[?(@.key=='vt-kpis')].kpis[?(@.key=='%s')].%s";
            mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath(String.format(base, "vt-mttr", "display")).value("2h 30m"))
                    .andExpect(jsonPath(String.format(base, "vt-mttr", "targetLine")).value("Target ≤ 4h"))
                    .andExpect(jsonPath(String.format(base, "vt-cost", "display")).value("USD 1,234.50"))
                    .andExpect(jsonPath(String.format(base, "vt-tickets", "display")).value("12"))
                    .andExpect(jsonPath(String.format(base, "vt-tickets", "status")).value("ON_TARGET"))
                    .andExpect(jsonPath(String.format(base, "vt-health", "display")).value("Amber"))
                    .andExpect(jsonPath(String.format(base, "vt-health", "status")).value("BELOW_TARGET"))
                    .andExpect(jsonPath("$.sections[?(@.key=='vt-kpis')].filled").value(true));
            mvc.perform(get("/api/meetings/" + m + "/dashboard").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.kpis[?(@.kpi.key=='vt-health')].trend.statuses[-1]").value("BELOW_TARGET"));
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore);
        }
    }

    @Test
    void textStatusNeedsOptions() throws Exception {
        ObjectNode draft = currentAsDraft();
        ObjectNode k = draft.putArray("kpiLibrary").addObject();
        k.put("key", "vt-bad").put("name", "Bad").put("unit", "").put("target", 0).put("direction", "HIGHER").put("amberThreshold", 0)
                .put("valueType", "TEXT_STATUS");
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("vt-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at least one option")));
    }
}
