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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SlidePlanEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void meetingSlidesReflectContent() throws Exception {
        String created = mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("sp-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2038-03\",\"meetingDate\":\"2038-03-25\"}")).andReturn().getResponse().getContentAsString();
        long m = json.readTree(created).get("id").asLong();
        JsonNode detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        JsonNode roadmap = null;
        for (JsonNode s : detail.get("sections")) {
            if (s.get("key").asText().equals("roadmap")) roadmap = s;
        }
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            rows.append(i == 0 ? "" : ",").append("[\"Initiative ").append(i).append("\",\"IAM Engineering\",\"On track\",\"Dec 2038\"]");
        }
        mvc.perform(put("/api/meetings/" + m + "/sections/" + roadmap.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"content\":{\"rows\":[" + rows + "]}}")).andExpect(status().isOk());

        mvc.perform(get("/api/meetings/" + m + "/slides").with(user("r").roles("END_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slides[0].layout").value("IAM_TITLE"))
                .andExpect(jsonPath("$.slides[0].data.subtitle").value("March 2038"))
                .andExpect(jsonPath("$.slidesPerSection.roadmap").value(Matchers.greaterThan(1)))
                .andExpect(jsonPath("$.font").isString());
    }
}
