package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class MeetingThemeTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode settings(String primary, String font) {
        ObjectNode s = json.createObjectNode();
        s.putObject("colors").put("primary", primary).put("text", "222222");
        s.putObject("fonts").put("heading", font).put("body", "Calibri");
        return s;
    }

    private ResultActions create(String name, ObjectNode settings) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("name", name);
        body.set("settings", settings);
        return mvc.perform(post("/api/admin/themes").with(csrf()).with(user("mt-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions update(long id, String name, ObjectNode settings, int basedOn) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("name", name);
        body.set("settings", settings);
        body.put("basedOnVersion", basedOn);
        return mvc.perform(put("/api/admin/themes/" + id).with(csrf()).with(user("mt-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private long meeting(String month, java.time.LocalDate date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("mt-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions choose(long m, long theme, String who, String role) throws Exception {
        return mvc.perform(put("/api/meetings/" + m + "/theme-choice").with(csrf()).with(user(who).roles(role))
                .contentType(MediaType.APPLICATION_JSON).content("{\"themeId\":" + theme + "}"));
    }

    private String accent(long m) throws Exception {
        return json.readTree(mvc.perform(get("/api/meetings/" + m + "/theme").with(user("r").roles("END_USER")))
                .andReturn().getResponse().getContentAsString()).at("/colors/accent1").asText();
    }

    @Test
    void preparingMeetingsFollowTheLatestVersionAndHeldOnesKeepTheirs() throws Exception {
        long a = json.readTree(create("Board pack", settings("004B87", "Arial")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        long preparing = meeting("2052-02", java.time.LocalDate.of(2052, 2, 26));
        mvc.perform(get("/api/meetings/" + preparing + "/theme-choice").with(user("e").roles("END_USER")))
                .andExpect(jsonPath("$.themeName").value("IAM Red"))
                .andExpect(jsonPath("$.canChange").value(true))
                .andExpect(jsonPath("$.frozen").value(false));
        choose(preparing, a, "e", "END_USER").andExpect(status().isOk()).andExpect(jsonPath("$.themeName").value("Board pack"));
        org.assertj.core.api.Assertions.assertThat(accent(preparing)).isEqualTo("004B87");

        java.time.LocalDate yesterday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Singapore")).minusDays(1);
        long held = meeting((yesterday.getYear() + 3000) + "-" + String.format("%02d", yesterday.getMonthValue()), yesterday);
        choose(held, a, "e", "END_USER").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("MEETING_HELD"));
        choose(held, a, "mt-admin", "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(true)).andExpect(jsonPath("$.versionNo").value(1));

        update(a, "Board pack", settings("00594F", "Arial"), 1).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(accent(preparing)).isEqualTo("00594F"); // follows the latest version
        org.assertj.core.api.Assertions.assertThat(accent(held)).isEqualTo("004B87");      // kept as presented
    }
}
