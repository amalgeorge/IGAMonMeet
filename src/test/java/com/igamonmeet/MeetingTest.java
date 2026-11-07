package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.meeting.ActionItem;
import com.igamonmeet.meeting.ActionItemRepository;
import com.igamonmeet.meeting.MeetingService;
import com.igamonmeet.template.TeamTag;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class MeetingTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ActionItemRepository actions;
    @Autowired MeetingService service;
    @Autowired JdbcTemplate jdbc;

    private ResultActions create(String month, String date) throws Exception {
        return mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("meeting-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}"));
    }

    private long createId(String month, String date) throws Exception {
        String body = create(month, date).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private int currentTemplateSections() throws Exception {
        String body = mvc.perform(get("/api/templates/current").with(user("u").roles("END_USER"))).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("sections").size();
    }

    @Test
    void newMeetingCopiesTemplateAndCarriesOpenActions() throws Exception {
        long previous = createId("2031-01", "2031-01-29");
        actions.save(new ActionItem(previous, "Open item", "J. Tan", TeamTag.OPERATIONS, LocalDate.of(2031, 2, 10), ActionItem.OPEN, null, 1, Instant.now()));
        actions.save(new ActionItem(previous, "Closed item", "R. Lim", TeamTag.ENGINEERING, null, ActionItem.CLOSED, null, 2, Instant.now()));

        long next = createId("2031-02", "2031-02-26");
        mvc.perform(get("/api/meetings/" + next).with(user("reader").roles("END_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meeting.title").value("IAM Monthly Meeting · February 2031"))
                .andExpect(jsonPath("$.meeting.label").value("PREPARING"))
                .andExpect(jsonPath("$.sections.length()").value(currentTemplateSections()))
                .andExpect(jsonPath("$.actionItems.length()").value(1))
                .andExpect(jsonPath("$.actionItems[0].title").value("Open item"))
                .andExpect(jsonPath("$.actionItems[0].originMeetingId").value(previous));
    }

    @Test
    void oneLiveMeetingPerMonth() throws Exception {
        createId("2032-03", "2032-03-25");
        create("2032-03", "2032-03-26").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("MONTH_EXISTS"));
    }

    @Test
    void pastMeetingIsHeldAndClosedToEndUsers() throws Exception {
        long id = createId("2020-05", "2020-05-20");
        mvc.perform(get("/api/meetings/" + id).with(user("reader").roles("END_USER")))
                .andExpect(jsonPath("$.meeting.label").value("HELD"))
                .andExpect(jsonPath("$.meeting.editWindowOpen").value(false))
                .andExpect(jsonPath("$.meeting.editableUntil").value("2020-05-22T15:59:59.999999999Z"));
    }

    @Test
    void softDeleteRestoreAndPurge() throws Exception {
        long id = createId("2033-04", "2033-04-24");
        mvc.perform(delete("/api/admin/meetings/" + id).with(csrf()).with(user("meeting-admin").roles("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/meetings/" + id).with(user("reader").roles("END_USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/meetings/deleted").with(user("meeting-admin").roles("ADMIN")))
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());

        mvc.perform(post("/api/admin/meetings/" + id + "/restore").with(csrf()).with(user("meeting-admin").roles("ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/meetings/" + id).with(user("reader").roles("END_USER"))).andExpect(status().isOk());

        mvc.perform(delete("/api/admin/meetings/" + id).with(csrf()).with(user("meeting-admin").roles("ADMIN")));
        jdbc.update("UPDATE meeting SET deleted_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(31L * 24 * 3600)), id);
        assertThat(service.purge()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meeting WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meeting_section WHERE meeting_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void endUserCanReadButNotCreateOrDelete() throws Exception {
        mvc.perform(get("/api/meetings").with(user("reader").roles("END_USER"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("reader").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2034-01\",\"meetingDate\":\"2034-01-25\"}"))
                .andExpect(status().isForbidden());
    }
}
