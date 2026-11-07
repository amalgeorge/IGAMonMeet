package com.igamonmeet;

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
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentEditTest {

    private static final String SUMMARY = "{\"points\":[\"Leaver removal above 99%\"],\"stats\":[]}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor endUser(String name) { return user(name).roles("END_USER"); }
    private static RequestPostProcessor admin() { return user("doc-admin").roles("ADMIN"); }

    private long meeting(String month, String date) throws Exception {
        String body = mvc.perform(post("/api/admin/meetings").with(csrf()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}")).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode section(long meetingId, String key) throws Exception {
        JsonNode d = json.readTree(mvc.perform(get("/api/meetings/" + meetingId).with(endUser("reader"))).andReturn().getResponse().getContentAsString());
        for (JsonNode s : d.get("sections")) {
            if (s.get("key").asText().equals(key)) return s;
        }
        throw new AssertionError("no section " + key);
    }

    private ResultActions lock(long m, long s, RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/meetings/" + m + "/sections/" + s + "/lock").with(csrf()).with(who));
    }

    private ResultActions save(long m, long s, long version, String content, RequestPostProcessor who) throws Exception {
        return mvc.perform(put("/api/meetings/" + m + "/sections/" + s).with(csrf()).with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + ",\"content\":" + content + "}"));
    }

    @Test
    void lockSaveAndConflict() throws Exception {
        long m = meeting("2035-01", "2035-01-25");
        long s = section(m, "summary").get("id").asLong();

        lock(m, s, endUser("user-a")).andExpect(status().isNoContent());
        lock(m, s, endUser("user-b")).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LOCKED"));
        save(m, s, 0, SUMMARY, endUser("user-b")).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LOCKED"));

        save(m, s, 0, SUMMARY, endUser("user-a")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/meetings/" + m).with(endUser("reader")))
                .andExpect(jsonPath("$.sections[?(@.key=='summary')].filled").value(true))
                .andExpect(jsonPath("$.sections[?(@.key=='summary')].lockedBy").value("user-a"));

        // A second tab still on version 0 gets the saved version back.
        save(m, s, 0, "{\"points\":[\"older\"],\"stats\":[]}", endUser("user-a"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.current.version").value(1))
                .andExpect(jsonPath("$.current.updatedBy").value("user-a"));

        mvc.perform(get("/api/admin/audit").param("actor", "user-a").param("action", "CHANGED").with(admin()))
                .andExpect(jsonPath("$.items[?(@.field=='summary.points')]").exists());

        mvc.perform(delete("/api/meetings/" + m + "/sections/" + s + "/lock").with(csrf()).with(endUser("user-a"))).andExpect(status().isNoContent());
        lock(m, s, endUser("user-b")).andExpect(status().isNoContent());
    }

    @Test
    void idleLockCanBeTakenOver() throws Exception {
        long m = meeting("2035-02", "2035-02-22");
        long s = section(m, "risks").get("id").asLong();
        lock(m, s, endUser("user-a")).andExpect(status().isNoContent());
        jdbc.update("UPDATE section_lock SET last_seen_at = ? WHERE section_id = ?", Timestamp.from(Instant.now().minusSeconds(16 * 60)), s);
        lock(m, s, endUser("user-b")).andExpect(status().isNoContent());
    }

    @Test
    void contentIsValidatedPerKind() throws Exception {
        long m = meeting("2035-03", "2035-03-29");
        JsonNode roadmap = section(m, "roadmap");
        int cols = roadmap.get("columns").size();
        save(m, roadmap.get("id").asLong(), 0, "{\"rows\":[[\"only one cell\"]]}", endUser("user-a"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_CONTENT"));
        StringBuilder row = new StringBuilder("[");
        for (int i = 0; i < cols; i++) row.append(i == 0 ? "" : ",").append("\"c").append(i).append('"');
        save(m, roadmap.get("id").asLong(), 0, "{\"rows\":[" + row + "]],\"notes\":\"Lead with wave 3\"}", endUser("user-a")).andExpect(status().isOk());
        save(m, roadmap.get("id").asLong(), 1, "{\"rows\":[],\"notes\":\"" + "x".repeat(4001) + "\"}", endUser("user-a"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_CONTENT"));
    }

    @Test
    void editWindowClosesForEndUsersOnly() throws Exception {
        long m = meeting("2019-06", "2019-06-27");
        long s = section(m, "summary").get("id").asLong();
        lock(m, s, endUser("user-a")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("EDIT_WINDOW_CLOSED"));
        save(m, s, 0, SUMMARY, endUser("user-a")).andExpect(status().isForbidden());
        mvc.perform(get("/api/meetings/" + m).with(endUser("reader"))).andExpect(jsonPath("$.canEdit").value(false));
        save(m, s, 0, SUMMARY, admin()).andExpect(status().isOk());
    }
}
