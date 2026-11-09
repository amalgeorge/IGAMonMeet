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
class KpiDisplayTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "kpi display test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private void publish(ObjectNode draft) throws Exception {
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("kd-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft))).andExpect(status().isCreated());
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        }
        throw new AssertionError(key);
    }

    private long meeting(String month, LocalDate date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("kd-admin").roles("ADMIN"))
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

    private void labels(String on, String below, String off) throws Exception {
        mvc.perform(put("/api/admin/status-labels").with(csrf()).with(user("kd-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ON_TARGET\":\"" + on + "\",\"BELOW_TARGET\":\"" + below + "\",\"OFF_TARGET\":\"" + off + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void displayFieldsAndRenamedStatusesReachTheMeetingAndSlides() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        ArrayNode lib = ((ArrayNode) json.readTree(mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString()).get("kpiLibrary")).deepCopy();
        for (JsonNode k : lib) {
            if (k.get("key").asText().equals("sla")) {
                ((ObjectNode) k).put("subtitle", "Monthly").put("description", "Share of requests done within SLA.").put("decimals", 1);
            }
        }
        draft.set("kpiLibrary", lib);
        publish(draft);
        labels("Green", "Watch", "Act now");
        try {
            mvc.perform(get("/api/status-labels").with(user("r").roles("END_USER"))).andExpect(jsonPath("$.BELOW_TARGET").value("Watch"));
            long m = meeting("2049-06", LocalDate.of(2049, 6, 24));
            // All required KPIs filled, or the slide shows "Not provided yet." instead of tiles.
            fill(m, "kpis", "{\"values\":{\"sla\":{\"value\":96.44},\"provisioning\":{\"value\":3},\"leaver\":{\"value\":100}}}");
            String k = "$.sections[?(@.key=='kpis')].kpis[?(@.key=='sla')].";
            mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath(k + "display").value("96.4%"))
                    .andExpect(jsonPath(k + "subtitle").value("Monthly"))
                    .andExpect(jsonPath(k + "description").value("Share of requests done within SLA."))
                    .andExpect(jsonPath(k + "statusLabel").value("Green"));
            mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.slides[?(@.layout=='IAM_KPI')].data.notes")
                            .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Share of requests done within SLA."))));
        } finally {
            labels("On target", "Below target", "Off target");
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore);
        }
    }

    @Test
    void blankStatusLabelIsRejectedAndOnlyAdminsRename() throws Exception {
        mvc.perform(put("/api/admin/status-labels").with(csrf()).with(user("kd-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ON_TARGET\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/status-labels").with(csrf()).with(user("e").roles("END_USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ON_TARGET\":\"X\"}")).andExpect(status().isForbidden());
    }
}
