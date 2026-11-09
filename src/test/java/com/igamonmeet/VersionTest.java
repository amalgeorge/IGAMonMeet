package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.meeting.MeetingTime;
import com.igamonmeet.version.VersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class VersionTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VersionService versions;
    @Autowired MeetingTime time;

    private static RequestPostProcessor eu() { return user("v-user").roles("END_USER"); }

    private long meeting(String month, String date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("v-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}")).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode summary(long m) throws Exception {
        for (JsonNode s : json.readTree(mvc.perform(get("/api/meetings/" + m).with(eu())).andReturn().getResponse().getContentAsString()).get("sections")) {
            if (s.get("key").asText().equals("summary")) return s;
        }
        throw new AssertionError();
    }

    private void setSummary(long m, String point) throws Exception {
        JsonNode s = summary(m);
        mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(eu()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + s.get("version").asLong() + ",\"content\":{\"points\":[\"" + point + "\"],\"stats\":[]}}")).andExpect(status().isOk());
    }

    @Test
    void saveDiffAndRestore() throws Exception {
        long m = meeting("2041-01", "2041-01-30");
        setSummary(m, "First draft");
        mvc.perform(post("/api/meetings/" + m + "/versions").with(csrf()).with(eu()).contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Before review\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(1));
        setSummary(m, "Second draft");

        mvc.perform(get("/api/meetings/" + m + "/versions/diff").param("from", "1").with(eu()))
                .andExpect(jsonPath("$[0].field").value("points"))
                .andExpect(jsonPath("$[0].before").value("[\"First draft\"]"))
                .andExpect(jsonPath("$[0].after").value("[\"Second draft\"]"));

        mvc.perform(post("/api/meetings/" + m + "/versions/1/restore").with(csrf()).with(eu()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restored[0]").value("summary"))
                .andExpect(jsonPath("$.beforeVersionNo").value(2));
        assertThat(summary(m).get("contentJson").asText()).contains("First draft");

        mvc.perform(get("/api/meetings/" + m + "/versions").with(eu()))
                .andExpect(jsonPath("$[0].label").value("Before restoring v1"))
                .andExpect(jsonPath("$[1].label").value("Before review"));
        mvc.perform(get("/api/meetings/" + m + "/versions/diff").param("from", "2").with(eu()))
                .andExpect(jsonPath("$[0].before").value("[\"Second draft\"]"));
    }

    @Test
    void meetingDaySnapshotIsTakenOnce() throws Exception {
        java.time.LocalDate day = java.time.LocalDate.of(2041, 2, 27);
        long m = meeting("2041-02", day.toString());
        assertThat(versions.snapshotHeldOn(day, "As presented")).isGreaterThanOrEqualTo(1);
        assertThat(versions.snapshotHeldOn(day, "As presented")).isZero();
        mvc.perform(get("/api/meetings/" + m + "/versions").with(eu()))
                .andExpect(jsonPath("$[0].reason").value("SNAPSHOT"))
                .andExpect(jsonPath("$[0].createdBy").value("system"));
    }

    @Test
    void restoreFollowsTheEditWindow() throws Exception {
        long m = meeting("2016-03", "2016-03-31");
        mvc.perform(post("/api/meetings/" + m + "/versions").with(csrf()).with(user("v-admin").roles("ADMIN"))).andExpect(status().isCreated());
        mvc.perform(post("/api/meetings/" + m + "/versions/1/restore").with(csrf()).with(eu()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("EDIT_WINDOW_CLOSED"));
        mvc.perform(post("/api/meetings/" + m + "/versions/1/restore").with(csrf()).with(user("v-admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/meetings/" + m + "/versions/diff").param("from", "1").with(eu())).andExpect(status().isOk());
    }
}
