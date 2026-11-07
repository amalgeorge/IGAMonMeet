package com.igamonmeet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ActionCommentTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String who, String role) {
        return b.with(csrf()).with(user(who).roles(role)).contentType(MediaType.APPLICATION_JSON);
    }

    private long meeting(String month, String date) throws Exception {
        String body = mvc.perform(as(post("/api/admin/meetings"), "ac-admin", "ADMIN")
                .content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}")).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long addAction(long m, String title, String due) throws Exception {
        String body = mvc.perform(as(post("/api/meetings/" + m + "/actions"), "eu", "END_USER")
                .content("{\"title\":\"" + title + "\",\"owner\":\"J. Tan\",\"teamTag\":\"OPERATIONS\",\"dueDate\":" + (due == null ? "null" : "\"" + due + "\"") + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private ResultActions edit(long m, long a, long version, String status) throws Exception {
        return mvc.perform(as(put("/api/meetings/" + m + "/actions/" + a), "eu", "END_USER")
                .content("{\"version\":" + version + ",\"title\":\"Renamed\",\"owner\":\"R. Lim\",\"teamTag\":\"ENGINEERING\",\"dueDate\":null,\"status\":\"" + status + "\"}"));
    }

    @Test
    void actionItemsOrderAndEdit() throws Exception {
        long m = meeting("2037-01", "2037-01-28");
        String past = LocalDate.now().minusDays(3).toString();
        long later = addAction(m, "Later", LocalDate.now().plusDays(30).toString());
        addAction(m, "Overdue", past);
        long closed = addAction(m, "Done already", null);
        edit(m, closed, 0, "CLOSED").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER")))
                .andExpect(jsonPath("$.actionItems[0].title").value("Overdue"))
                .andExpect(jsonPath("$.actionItems[0].overdue").value(true))
                .andExpect(jsonPath("$.actionItems[1].title").value("Later"))
                .andExpect(jsonPath("$.actionItems[2].status").value("CLOSED"));

        edit(m, closed, 0, "OPEN").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VERSION_CONFLICT"));
        edit(m, later, 0, "MAYBE").andExpect(status().isBadRequest());
        mvc.perform(delete("/api/meetings/" + m + "/actions/" + later).with(csrf()).with(user("eu").roles("END_USER"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andExpect(jsonPath("$.actionItems.length()").value(2));
    }

    @Test
    void actionsFollowEditWindowButCommentsDoNot() throws Exception {
        long m = meeting("2018-02", "2018-02-22");
        mvc.perform(as(post("/api/meetings/" + m + "/actions"), "eu", "END_USER")
                .content("{\"title\":\"x\",\"owner\":\"y\",\"teamTag\":\"SHARED\"}")).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/meetings/" + m + "/comments"), "eu", "END_USER")
                .content("{\"sectionKey\":\"summary\",\"body\":\"Still worth asking\"}")).andExpect(status().isCreated());
    }

    @Test
    void commentThreads() throws Exception {
        long m = meeting("2037-02", "2037-02-25");
        String body = mvc.perform(as(post("/api/meetings/" + m + "/comments"), "chair", "END_USER")
                .content("{\"sectionKey\":\"kpis\",\"body\":\"Which two BUs?\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long root = json.readTree(body).get("id").asLong();
        String reply = mvc.perform(as(post("/api/meetings/" + m + "/comments"), "ops", "END_USER")
                .content("{\"sectionKey\":\"kpis\",\"parentId\":" + root + ",\"body\":\"Finance and Retail.\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long replyId = json.readTree(reply).get("id").asLong();

        mvc.perform(as(post("/api/meetings/" + m + "/comments"), "ops", "END_USER")
                .content("{\"sectionKey\":\"kpis\",\"parentId\":" + replyId + ",\"body\":\"nested\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/meetings/" + m + "/comments"), "ops", "END_USER")
                .content("{\"sectionKey\":\"no-such\",\"body\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/meetings/" + m + "/comments"), "ops", "END_USER")
                .content("{\"sectionKey\":\"kpis\",\"body\":\"  \"}")).andExpect(status().isBadRequest());

        mvc.perform(as(post("/api/meetings/" + m + "/comments/" + root + "/resolve"), "chair", "END_USER")).andExpect(status().isNoContent());
        mvc.perform(get("/api/meetings/" + m + "/comments").with(user("r").roles("END_USER")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resolved").value(true))
                .andExpect(jsonPath("$[0].resolvedBy").value("chair"))
                .andExpect(jsonPath("$[0].replies[0].body").value("Finance and Retail."));
    }
}
