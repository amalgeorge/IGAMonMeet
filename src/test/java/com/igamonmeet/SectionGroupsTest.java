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
class SectionGroupsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "groups test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private void publish(ObjectNode draft) throws Exception {
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("grp-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft))).andExpect(status().isCreated());
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        }
        throw new AssertionError(key);
    }

    private long meeting(String month, LocalDate date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("grp-admin").roles("ADMIN"))
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

    private org.springframework.test.web.servlet.ResultActions publishRaw(ObjectNode draft) throws Exception {
        return mvc.perform(post("/api/admin/templates").with(csrf()).with(user("grp-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)));
    }

    private ObjectNode group(String key, String name, String visibility) {
        ObjectNode g = json.createObjectNode();
        g.put("key", key).put("name", name).put("visibility", visibility).put("divider", true);
        return g;
    }

    @Test
    void groupsGatherSectionsAndARetiredGroupRetiresItsSections() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        draft.putArray("groups").add(group("ops-review", "Operations review", "SHOWN")).add(group("old", "Old stuff", "RETIRED"));
        for (String k : List.of("kpis", "sla-trend", "sitrep")) section(draft, k).put("groupKey", "ops-review");
        section(draft, "releases").put("groupKey", "old");
        publish(draft);
        try {
            mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.groups[0].name").value("Operations review"))
                    .andExpect(jsonPath("$.sections[?(@.key=='sitrep')].groupKey").value("ops-review"));
            long m = meeting("2046-04", LocalDate.of(2046, 4, 29));
            mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='kpis')].groupName").value("Operations review"))
                    .andExpect(jsonPath("$.sections[?(@.key=='summary')].groupKey").value(org.hamcrest.Matchers.contains((Object) null)))
                    .andExpect(jsonPath("$.sections[?(@.key=='releases')]").isEmpty());
            mvc.perform(get("/api/meetings/" + m + "/dashboard").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.kpis[?(@.kpi.key=='sla')].group").value("Operations review"));
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            restore.putArray("groups");
            publish(restore);
        }
    }

    @Test
    void groupSectionsMustBeAdjacentAndGroupsMustExist() throws Exception {
        ObjectNode draft = currentAsDraft();
        draft.putArray("groups").add(group("g", "G", "SHOWN"));
        section(draft, "summary").put("groupKey", "g");
        section(draft, "sitrep").put("groupKey", "g");
        publishRaw(draft).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("next to each other")));
        ObjectNode bad = currentAsDraft();
        section(bad, "summary").put("groupKey", "nope");
        publishRaw(bad).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not exist")));
    }
}
