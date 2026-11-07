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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igamonmeet.pptximport.SlideMatcher;
import java.util.HashMap;
import java.util.Map;
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
class ImportTest {

    private static final Map<String, String> CONTENT = Map.of(
            "summary", "{\"points\":[\"Leaver removal above 99%\",\"Certification behind plan\"],\"stats\":[],\"notes\":\"Start with the good news.\"}",
            "kpis", "{\"values\":{\"sla\":{\"value\":96.4},\"provisioning\":{\"value\":3.2},\"leaver\":{\"value\":99.1}},\"commentary\":\"Steady.\"}",
            "roadmap", "{\"rows\":[[\"SSO wave 3\",\"IAM Engineering\",\"On track\",\"Dec\"],[\"PAM upgrade\",\"IAM Engineering\",\"At risk\",\"Jan\"]]}",
            "risks", "{\"leftHead\":\"Risks\",\"left\":[\"Vendor API change\"],\"rightHead\":\"Decisions needed\",\"right\":[\"Approve escalation\"]}");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long meeting(String month, String date) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("im-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}")).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, JsonNode> sections(long m) throws Exception {
        Map<String, JsonNode> out = new HashMap<>();
        json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString())
                .get("sections").forEach(s -> out.put(s.get("key").asText(), s));
        return out;
    }

    private void fill(long m, String key, String content) throws Exception {
        JsonNode s = sections(m).get(key);
        mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + s.get("version").asLong() + ",\"content\":" + content + "}"))
                .andExpect(status().isOk());
    }

    private byte[] exported(long m) throws Exception {
        return mvc.perform(post("/api/meetings/" + m + "/export/pptx").with(csrf()).with(user("r").roles("END_USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sections\":null,\"notes\":true,\"comments\":false,\"versionStamp\":true}")).andReturn().getResponse().getContentAsByteArray();
    }

    private ResultActions upload(long m, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/meetings/" + m + "/imports").file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .with(csrf()).with(user("e").roles("END_USER")));
    }

    /** Accepts every suggestion that names a Section. */
    private ResultActions applySuggestions(long m, JsonNode proposal, String mode) throws Exception {
        ObjectNode body = json.createObjectNode().put("mode", mode);
        ArrayNode maps = body.putArray("mappings");
        proposal.get("slides").forEach(s -> {
            if (!s.get("sectionKey").isNull()) maps.addObject().put("n", s.get("n").asInt()).put("sectionKey", s.get("sectionKey").asText());
        });
        return mvc.perform(post("/api/meetings/" + m + "/imports/" + proposal.get("importId").asLong() + "/apply").with(csrf()).with(user("e").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void exportedDeckImportsBackIntoTheSameSections() throws Exception {
        long source = meeting("2040-01", "2040-01-30");
        for (var e : CONTENT.entrySet()) fill(source, e.getKey(), e.getValue());
        byte[] deck = exported(source);

        long target = meeting("2040-02", "2040-02-27");
        JsonNode proposal = json.readTree(upload(target, "IAM_Jan_2040.pptx", deck).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        Map<String, Double> confidence = new HashMap<>();
        proposal.get("slides").forEach(s -> { if (!s.get("sectionKey").isNull()) confidence.put(s.get("sectionKey").asText(), s.get("confidence").asDouble()); });
        assertThat(confidence).containsKeys("summary", "kpis", "roadmap", "risks");
        assertThat(confidence.get("roadmap")).isEqualTo(1.0);

        applySuggestions(target, proposal, "REPLACE").andExpect(status().isOk()).andExpect(jsonPath("$.skipped.length()").value(0));

        Map<String, JsonNode> after = sections(target);
        for (String key : CONTENT.keySet()) {
            JsonNode want = json.readTree(CONTENT.get(key));
            JsonNode got = json.readTree(after.get(key).get("contentJson").asText());
            switch (key) {
                case "summary" -> {
                    assertThat(got.get("points")).isEqualTo(want.get("points"));
                    assertThat(got.get("notes").asText()).isEqualTo("Start with the good news.");
                }
                case "kpis" -> {
                    assertThat(got.at("/values/sla/value").decimalValue()).isEqualByComparingTo("96.4");
                    assertThat(got.at("/values/provisioning/value").decimalValue()).isEqualByComparingTo("3.2");
                    assertThat(got.get("commentary").asText()).isEqualTo("Steady.");
                }
                case "roadmap" -> assertThat(got.get("rows")).isEqualTo(want.get("rows"));
                case "risks" -> {
                    assertThat(got.get("left")).isEqualTo(want.get("left"));
                    assertThat(got.get("right")).isEqualTo(want.get("right"));
                }
                default -> { }
            }
        }

        applySuggestions(target, proposal, "REPLACE").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_APPLIED"));
    }

    @Test
    void keepModeLeavesExistingContentAndAddsANote() throws Exception {
        long source = meeting("2040-03", "2040-03-26");
        fill(source, "roadmap", CONTENT.get("roadmap"));
        long target = meeting("2040-04", "2040-04-30");
        fill(target, "roadmap", "{\"rows\":[[\"Mine\",\"Me\",\"Done\",\"Now\"]]}");

        JsonNode proposal = json.readTree(upload(target, "deck.pptx", exported(source)).andReturn().getResponse().getContentAsString());
        applySuggestions(target, proposal, "KEEP").andExpect(status().isOk()).andExpect(jsonPath("$.keptWithNote[0]").value("roadmap"));

        JsonNode got = json.readTree(sections(target).get("roadmap").get("contentJson").asText());
        assertThat(got.at("/rows/0/0").asText()).isEqualTo("Mine");
        assertThat(got.get("notes").asText()).contains("Imported from slides").contains("SSO wave 3");
    }

    @Test
    void wrongFilesAndClosedMeetingsAreRefused() throws Exception {
        long m = meeting("2040-05", "2040-05-28");
        upload(m, "old.ppt", new byte[] {1, 2, 3}).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("save it as .pptx")));
        upload(m, "macro.pptm", new byte[] {1}).andExpect(status().isBadRequest());
        upload(m, "broken.pptx", "nope".getBytes()).andExpect(status().isBadRequest());

        long closed = meeting("2017-07", "2017-07-27");
        upload(closed, "deck.pptx", exported(m)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("EDIT_WINDOW_CLOSED"));
    }

    @Test
    void titlesAreCleanedAndMatched() {
        assertThat(SlideMatcher.cleanTitle("Roadmap (2 of 3)")).isEqualTo("Roadmap");
        assertThat(SlideMatcher.cleanTitle("Appendix: Roadmap")).isEqualTo("Roadmap");
        assertThat(SlideMatcher.cleanTitle("Incidents (cont.)")).isEqualTo("Incidents");
    }
}
