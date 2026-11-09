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
class AvailableItemsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "available test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private void publish(ObjectNode draft) throws Exception {
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("av-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft))).andExpect(status().isCreated());
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        }
        throw new AssertionError(key);
    }

    private long meeting(String month, LocalDate date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("av-admin").roles("ADMIN"))
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

    @Test
    void editorsSwitchAvailableItemsOnAndOffWhilePreparing() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        section(draft, "releases").put("visibility", "AVAILABLE");
        ArrayNode kpis = (ArrayNode) section(draft, "kpis").get("kpis");
        String kpi = kpis.get(kpis.size() - 1).get("key").asText();
        ((ObjectNode) kpis.get(kpis.size() - 1)).put("visibility", "AVAILABLE");
        publish(draft);
        try {
            long m = meeting("2045-03", LocalDate.of(2045, 3, 27));
            assertThat(keys(detail(m))).doesNotContain("releases");

            mvc.perform(get("/api/meetings/" + m + "/available").with(user("e").roles("END_USER")))
                    .andExpect(jsonPath("$.open").value(true))
                    .andExpect(jsonPath("$.sections[?(@.key=='releases')]").isNotEmpty())
                    .andExpect(jsonPath("$.kpis[?(@.key=='" + kpi + "')].sectionKey").value("kpis"));

            mvc.perform(post("/api/meetings/" + m + "/available/sections/releases").with(csrf()).with(user("e").roles("END_USER")))
                    .andExpect(status().isNoContent());
            mvc.perform(post("/api/meetings/" + m + "/available/sections/kpis/kpis/" + kpi).with(csrf()).with(user("e").roles("END_USER")))
                    .andExpect(status().isNoContent());

            JsonNode d = detail(m);
            List<String> order = keys(d);
            assertThat(order.indexOf("releases")).isEqualTo(order.indexOf("roadmap") + 1); // Template position
            mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='releases')].switchedOn").value(true))
                    .andExpect(jsonPath("$.sections[?(@.key=='kpis')].kpis[?(@.key=='" + kpi + "')]").isNotEmpty());

            JsonNode rel = null;
            for (JsonNode s : d.get("sections")) if (s.get("key").asText().equals("releases")) rel = s;
            mvc.perform(put("/api/meetings/" + m + "/sections/" + rel.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":" + rel.get("version").asLong() + ",\"content\":{\"rows\":[[\"R1\",\"2045-03-01\",\"SSO\"]]}}"))
                    .andExpect(status().isOk());

            mvc.perform(delete("/api/meetings/" + m + "/available/sections/releases").with(csrf()).with(user("e").roles("END_USER")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("HAS_CONTENT"));
            mvc.perform(delete("/api/meetings/" + m + "/available/sections/releases").with(csrf()).with(user("av-admin").roles("ADMIN")))
                    .andExpect(status().isNoContent());
            mvc.perform(delete("/api/meetings/" + m + "/available/sections/kpis/kpis/" + kpi).with(csrf()).with(user("e").roles("END_USER")))
                    .andExpect(status().isNoContent());
            assertThat(keys(detail(m))).doesNotContain("releases");
            mvc.perform(delete("/api/meetings/" + m + "/available/sections/roadmap").with(csrf()).with(user("e").roles("END_USER")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_SWITCHABLE"));

            // Switching back on brings the hidden content back.
            mvc.perform(post("/api/meetings/" + m + "/available/sections/releases").with(csrf()).with(user("e").roles("END_USER")))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='releases')].contentJson").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("SSO"))));

            LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Singapore")).minusDays(1);
            long held = meeting(yesterday.getYear() + 2000 + "-" + String.format("%02d", yesterday.getMonthValue()), yesterday);
            mvc.perform(post("/api/meetings/" + held + "/available/sections/releases").with(csrf()).with(user("e").roles("END_USER")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("MEETING_HELD"));
            mvc.perform(get("/api/meetings/" + held + "/available").with(user("e").roles("END_USER")))
                    .andExpect(jsonPath("$.open").value(false));
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore);
        }
    }
}
