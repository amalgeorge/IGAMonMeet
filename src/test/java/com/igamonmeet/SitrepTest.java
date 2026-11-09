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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.meeting.SitrepUpgrade;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** SITREP replaces Incidents (docs/spec-v3.md §1.3). */
@SpringBootTest
@AutoConfigureMockMvc
class SitrepTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SitrepUpgrade upgrade;

    private JsonNode current() throws Exception {
        return json.readTree(mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
    }

    private long meeting(String month, String date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("st-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode detail(long m) throws Exception {
        return json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
    }

    private JsonNode section(JsonNode detail, String key) {
        for (JsonNode s : detail.get("sections")) if (s.get("key").asText().equals(key)) return s;
        return null;
    }

    private ResultActions save(long m, JsonNode s, String content) throws Exception {
        return mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + s.get("version").asLong() + ",\"content\":" + content + "}"));
    }

    @Test
    void templateHasSitrepInsteadOfIncidents() throws Exception {
        upgrade.upgrade(); // other tests may have published a Template without it
        List<String> keys = new ArrayList<>();
        JsonNode sitrep = null;
        for (JsonNode s : current().get("sections")) {
            keys.add(s.get("key").asText());
            if (s.get("key").asText().equals("sitrep")) sitrep = s;
        }
        assertThat(keys).contains("sitrep").doesNotContain("incidents");
        assertThat(sitrep.get("name").asText()).isEqualTo("SITREP");
        List<String> cols = new ArrayList<>();
        sitrep.get("columns").forEach(c -> cols.add(c.asText()));
        assertThat(cols).containsExactly("Type", "New", "Closed", "Pending", "Above SLA");
        assertThat(sitrep.at("/tableStyle/total").asBoolean()).isTrue();
        assertThat(sitrep.at("/tableStyle/alertColumn").asInt()).isEqualTo(4);

        JsonNode types = json.readTree(mvc.perform(get("/api/sitrep-types").with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        assertThat(types.get(0).asText()).isEqualTo("Incident");
    }

    @Test
    void countsAreWholeNumbersAndSlidesGetATotalAndAlert() throws Exception {
        upgrade.upgrade();
        long m = meeting("2034-02", "2034-02-16");
        JsonNode s = section(detail(m), "sitrep");
        save(m, s, "{\"rows\":[[\"Incident\",\"3\",\"2\",\"1.5\",\"0\"]]}").andExpect(status().isBadRequest());
        save(m, s, "{\"rows\":[[\"Incident\",\"3\",\"2\",\"1\",\"0\"],[\"Problem\",\"1\",\"0\",\"4\",\"2\"]]}").andExpect(status().isOk());

        JsonNode slides = json.readTree(mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        JsonNode table = null;
        for (JsonNode sl : slides.get("slides")) if ("sitrep".equals(sl.path("sectionKey").asText())) table = sl;
        assertThat(table).isNotNull();
        ArrayNode rows = (ArrayNode) table.at("/data/rows");
        assertThat(rows.get(2).toString()).isEqualTo("[\"Total\",\"4\",\"2\",\"5\",\"2\"]");
        assertThat(table.at("/data/alertColumn").asInt()).isEqualTo(4);
    }

    @Test
    void preparingMeetingsMoveFromIncidentsToSitrep() throws Exception {
        // Put an Incidents Section back in the Template, make a Meeting from it, then run the upgrade again.
        upgrade.upgrade();
        JsonNode cur = current();
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "incidents back for the upgrade test");
        ArrayNode secs = draft.putArray("sections");
        for (JsonNode s : cur.get("sections")) {
            if (!s.get("key").asText().equals("sitrep")) { secs.add(s); continue; }
            ObjectNode inc = s.deepCopy();
            inc.put("key", "incidents").put("name", "Incidents");
            inc.remove("tableStyle");
            ArrayNode cols = inc.putArray("columns");
            cols.add("Incident").add("Impact").add("Status").add("Resolved");
            secs.add(inc);
        }
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("st-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft))).andExpect(status().isCreated());
        long m = meeting("2034-05", "2034-05-18");
        JsonNode inc = section(detail(m), "incidents");
        save(m, inc, "{\"rows\":[[\"Outage\",\"High\",\"Closed\",\"Yes\"]]}").andExpect(status().isOk());

        upgrade.upgrade();

        JsonNode after = detail(m);
        assertThat(section(after, "sitrep")).isNotNull();
        assertThat(section(after, "incidents")).isNull(); // hidden; its rows stay on the Meeting
        List<String> keys = new ArrayList<>();
        current().get("sections").forEach(s -> keys.add(s.get("key").asText()));
        assertThat(keys).contains("sitrep").doesNotContain("incidents");
    }
}
