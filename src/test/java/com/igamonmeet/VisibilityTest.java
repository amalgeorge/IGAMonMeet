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

@SpringBootTest
@AutoConfigureMockMvc
class VisibilityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andReturn().getResponse().getContentAsString());
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "visibility test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private ResultActions publish(ObjectNode draft) throws Exception {
        return mvc.perform(post("/api/admin/templates").with(csrf()).with(user("vis-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)));
    }

    private ObjectNode section(ObjectNode draft, String key) {
        for (JsonNode s : draft.get("sections")) {
            if (s.get("key").asText().equals(key)) return (ObjectNode) s;
        }
        throw new AssertionError("no section " + key);
    }

    @Test
    void newMeetingsShowOnlyShownItemsAndOffSlideSectionsStayOffTheSlides() throws Exception {
        ObjectNode original = currentAsDraft();
        ObjectNode draft = original.deepCopy();
        section(draft, "releases").put("visibility", "AVAILABLE");
        section(draft, "access-reviews").put("visibility", "RETIRED");
        section(draft, "risks").put("onSlides", false);
        ArrayNode kpis = (ArrayNode) section(draft, "kpis").get("kpis");
        String retiredKpi = kpis.get(kpis.size() - 1).get("key").asText();
        ((ObjectNode) kpis.get(kpis.size() - 1)).put("visibility", "RETIRED");
        publish(draft).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sections[?(@.key=='releases')].visibility").value("AVAILABLE"))
                .andExpect(jsonPath("$.sections[?(@.key=='risks')].onSlides").value(false));

        try {
            long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("vis-admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2044-01\",\"meetingDate\":\"2044-01-28\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

            mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.sections[?(@.key=='releases')]").isEmpty())
                    .andExpect(jsonPath("$.sections[?(@.key=='access-reviews')]").isEmpty())
                    .andExpect(jsonPath("$.sections[?(@.key=='risks')].onSlides").value(false))
                    .andExpect(jsonPath("$.sections[?(@.key=='kpis')].kpis[?(@.key=='" + retiredKpi + "')]").isEmpty());

            mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slidesPerSection.risks").doesNotExist())
                    .andExpect(jsonPath("$.slidesPerSection.summary").exists()); // required, empty, on slides
        } finally {
            ObjectNode restore = currentAsDraft();
            restore.set("sections", original.get("sections"));
            publish(restore).andExpect(status().isCreated());
        }
    }

    @Test
    void atLeastOneSectionMustBeShown() throws Exception {
        ObjectNode draft = currentAsDraft();
        for (JsonNode s : draft.get("sections")) {
            ((ObjectNode) s).put("visibility", "AVAILABLE");
        }
        publish(draft).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("At least one Section must be Shown."));
    }

    @Test
    void chartCannotShowRetiredKpi() throws Exception {
        ObjectNode draft = currentAsDraft();
        for (JsonNode k : section(draft, "kpis").get("kpis")) {
            if (k.get("key").asText().equals("sla")) ((ObjectNode) k).put("visibility", "RETIRED");
        }
        publish(draft).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("shows a Retired KPI")));
    }
}
