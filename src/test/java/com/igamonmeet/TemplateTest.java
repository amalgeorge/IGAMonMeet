package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
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
class TemplateTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode currentAsDraft() throws Exception {
        String body = mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode cur = json.readTree(body);
        ObjectNode draft = json.createObjectNode();
        draft.put("basedOnVersion", cur.get("versionNo").asInt());
        draft.put("note", "test");
        draft.set("sections", cur.get("sections").deepCopy());
        return draft;
    }

    private ResultActions publish(ObjectNode draft) throws Exception {
        return mvc.perform(post("/api/admin/templates").with(csrf()).with(user("template-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft)));
    }

    @Test
    void seededTemplateMatchesMockup() throws Exception {
        mvc.perform(get("/api/templates/1").with(user("anyone").roles("END_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(9))
                .andExpect(jsonPath("$.sections[1].key").value("kpis"))
                .andExpect(jsonPath("$.sections[1].kpis.length()").value(4))
                .andExpect(jsonPath("$.sections[3].columns[0]").value("Incident"));
    }

    @Test
    void publishingCreatesNewVersionAndKeepsOldOne() throws Exception {
        ObjectNode draft = currentAsDraft();
        int based = draft.get("basedOnVersion").asInt();
        ((ObjectNode) draft.get("sections").get(0)).put("name", "Summary v" + (based + 1));
        publish(draft).andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(based + 1));

        mvc.perform(get("/api/templates/current").with(user("anyone").roles("END_USER")))
                .andExpect(jsonPath("$.versionNo").value(based + 1))
                .andExpect(jsonPath("$.sections[0].name").value("Summary v" + (based + 1)));
        mvc.perform(get("/api/templates/1").with(user("anyone").roles("END_USER")))
                .andExpect(jsonPath("$.sections[0].name").value("Executive summary"));
    }

    @Test
    void staleDraftIsRejected() throws Exception {
        ObjectNode draft = currentAsDraft();
        draft.put("basedOnVersion", draft.get("basedOnVersion").asInt() - 1);
        publish(draft).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("TEMPLATE_CHANGED"));
    }

    @Test
    void invalidDraftsAreRejected() throws Exception {
        ObjectNode dupKey = currentAsDraft();
        ((ObjectNode) dupKey.get("sections").get(1)).put("key", dupKey.get("sections").get(0).get("key").asText());
        publish(dupKey).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_TEMPLATE"));

        ObjectNode badLayout = currentAsDraft();
        ((ObjectNode) badLayout.get("sections").get(0)).put("layout", "IAM_KPI");
        publish(badLayout).andExpect(status().isBadRequest());

        ObjectNode badAmber = currentAsDraft();
        ObjectNode kpi = (ObjectNode) badAmber.get("sections").get(1).get("kpis").get(0);
        kpi.put("direction", "HIGHER");
        kpi.put("amberThreshold", kpi.get("target").decimalValue().add(java.math.BigDecimal.ONE));
        publish(badAmber).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amber")));

        ObjectNode noCols = currentAsDraft();
        ((ObjectNode) noCols.get("sections").get(3)).set("columns", json.createArrayNode());
        publish(noCols).andExpect(status().isBadRequest());

        ObjectNode chartOrphan = currentAsDraft();
        ArrayNode secs = (ArrayNode) chartOrphan.get("sections");
        for (int i = 0; i < secs.size(); i++) {
            if ("CHART".equals(secs.get(i).get("kind").asText())) {
                ((ObjectNode) secs.get(i)).put("chartKpiKey", "no-such-kpi");
            }
        }
        publish(chartOrphan).andExpect(status().isBadRequest());
    }

    @Test
    void endUserCannotPublish() throws Exception {
        String draft = json.writeValueAsString(currentAsDraft());
        mvc.perform(post("/api/admin/templates").with(csrf()).with(user("plain").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content(draft)).andExpect(status().isForbidden());
        assertThat(draft).contains("sections");
    }
}
