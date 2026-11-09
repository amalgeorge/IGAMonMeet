package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.meeting.ProgrammeUpgrade;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The Programme group and its Available Sections (docs/spec-v3.md §3). */
@SpringBootTest
@AutoConfigureMockMvc
class ProgrammeSectionsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProgrammeUpgrade upgrade;

    @Test
    void programmeSectionsAreAvailableAndCanBeSwitchedOn() throws Exception {
        upgrade.upgrade(); // other tests may have published a Template without them
        JsonNode cur = json.readTree(mvc.perform(get("/api/templates/current").with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        List<String> programme = new ArrayList<>();
        for (JsonNode s : cur.get("sections")) {
            if ("programme".equals(s.path("groupKey").asText())) {
                programme.add(s.get("key").asText());
                assertThat(s.get("visibility").asText()).isEqualTo("AVAILABLE");
            }
        }
        assertThat(programme).containsExactly("access-reviews", "privileged-id", "jml", "app-onboarding", "decisions", "wins");
        List<String> lib = new ArrayList<>();
        cur.get("kpiLibrary").forEach(k -> lib.add(k.get("key").asText()));
        assertThat(lib).contains("vault-coverage", "sessions-recorded", "break-glass");

        long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("ps-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2034-11\",\"meetingDate\":\"2034-11-16\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        List<String> keys = new ArrayList<>();
        detail.get("sections").forEach(s -> keys.add(s.get("key").asText()));
        assertThat(keys).doesNotContain("access-reviews", "wins");

        mvc.perform(post("/api/meetings/" + m + "/available/sections/jml").with(csrf()).with(user("ps-admin").roles("ADMIN")))
                .andExpect(status().is2xxSuccessful());
        detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        JsonNode jml = null;
        for (JsonNode s : detail.get("sections")) if (s.get("key").asText().equals("jml")) jml = s;
        assertThat(jml).isNotNull();
        assertThat(jml.get("groupName").asText()).isEqualTo("Programme");
    }
}
