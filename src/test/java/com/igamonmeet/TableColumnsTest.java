package com.igamonmeet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Template table columns: commas allowed, up to 12, named (spec-v3 §1.1). */
@SpringBootTest
@AutoConfigureMockMvc
class TableColumnsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "columns test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private ResultActions publish(ObjectNode draft) throws Exception {
        return mvc.perform(post("/api/admin/templates").with(csrf()).with(user("tc-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)));
    }

    private ObjectNode roadmap(ObjectNode draft) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals("roadmap")) return (ObjectNode) s;
        }
        throw new AssertionError("roadmap");
    }

    private void columns(ObjectNode draft, int n) {
        ObjectNode s = roadmap(draft);
        s.remove("tableStyle");
        ArrayNode cols = s.putArray("columns");
        cols.add("Owner, team");
        for (int i = 2; i <= n; i++) cols.add("Column " + i);
    }

    @Test
    void twelveColumnsWithCommasPublishThirteenDoNot() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode tooMany = currentAsDraft();
        columns(tooMany, 13);
        publish(tooMany).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("up to 12 columns")));

        ObjectNode blank = currentAsDraft();
        columns(blank, 3);
        ((ArrayNode) roadmap(blank).get("columns")).set(1, json.getNodeFactory().textNode(" "));
        publish(blank).andExpect(status().isBadRequest());

        ObjectNode ok = currentAsDraft();
        columns(ok, 12);
        publish(ok).andExpect(status().isCreated());
        try {
            mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='roadmap')].columns[0]").value("Owner, team"))
                    .andExpect(jsonPath("$.sections[?(@.key=='roadmap')].columns[11]").value("Column 12"));
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore).andExpect(status().isCreated());
        }
    }
}
