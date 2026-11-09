package com.igamonmeet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long meeting(String month, String date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("d-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}")).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void fill(long m, String key, String content) throws Exception {
        for (JsonNode s : json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString()).get("sections")) {
            if (s.get("key").asText().equals(key)) {
                mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + s.get("version").asLong() + ",\"content\":" + content + "}"))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void dashboardShowsTrendsHealthActionsAndComments() throws Exception {
        long sep = meeting("2042-09", "2042-09-25");
        fill(sep, "kpis", "{\"values\":{\"sla\":{\"value\":94.1}}}");
        long oct = meeting("2042-10", "2042-10-30");
        fill(oct, "kpis", "{\"values\":{\"sla\":{\"value\":96.4}}}");
        fill(oct, "roadmap", "{\"rows\":[[\"A\",\"x\",\"On track\",\"\"],[\"B\",\"x\",\"At risk\",\"\"],[\"C\",\"x\",\"On track\",\"\"]]}");
        mvc.perform(post("/api/meetings/" + oct + "/actions").with(csrf()).with(user("e").roles("END_USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Late item\",\"owner\":\"J\",\"teamTag\":\"OPERATIONS\",\"dueDate\":\"" + LocalDate.now().minusDays(5) + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/meetings/" + oct + "/comments").with(csrf()).with(user("chair").roles("END_USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionKey\":\"kpis\",\"body\":\"Why the jump?\"}")).andExpect(status().isCreated());

        JsonNode dash = json.readTree(mvc.perform(get("/api/meetings/" + oct + "/dashboard").with(user("r").roles("END_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[?(@.kpi.key=='sla')].trend.labels[-2]").value("Sep 42"))
                .andExpect(jsonPath("$.kpis[?(@.kpi.key=='sla')].trend.values[-2]").value(94.1))
                .andExpect(jsonPath("$.kpis[?(@.kpi.key=='sla')].trend.values[-1]").value(96.4))
                .andExpect(jsonPath("$.health[?(@.sectionKey=='roadmap')].counts['On track']").value(2))
                .andExpect(jsonPath("$.health[?(@.sectionKey=='roadmap')].total").value(3))
                .andExpect(jsonPath("$.actions[?(@.title=='Late item')].overdue").value(true))
                .andExpect(jsonPath("$.openComments").value(1))
                .andExpect(jsonPath("$.comments[0].body").value("Why the jump?"))
                .andReturn().getResponse().getContentAsString());
        // Earlier tests' open items carry forward, so compare against the list rather than a fixed number.
        int overdue = 0;
        for (JsonNode a : dash.get("actions")) {
            if (a.get("overdue").asBoolean()) overdue++;
        }
        org.assertj.core.api.Assertions.assertThat(dash.get("overdueActions").asInt()).isEqualTo(overdue).isPositive();
        org.assertj.core.api.Assertions.assertThat(dash.get("openActions").asInt()).isEqualTo(dash.get("actions").size());
    }
}
