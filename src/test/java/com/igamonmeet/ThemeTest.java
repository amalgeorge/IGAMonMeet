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
class ThemeTest {

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
        return mvc.perform(post("/api/admin/themes").with(csrf()).with(user("th-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions update(long id, ObjectNode settings, int basedOn) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("name", "Quarterly review");
        body.set("settings", settings);
        body.put("basedOnVersion", basedOn);
        return mvc.perform(put("/api/admin/themes/" + id).with(csrf()).with(user("th-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void seededDefaultThemeExists() throws Exception {
        mvc.perform(get("/api/themes").with(user("r").roles("END_USER")))
                .andExpect(jsonPath("$[?(@.name=='IAM Red')].isDefault").value(true))
                .andExpect(jsonPath("$[?(@.name=='IAM Red')].settings.colors.primary").value("C8102E"));
    }

    @Test
    void themesAreValidatedVersionedAndOnlyAdminsEditThem() throws Exception {
        create("Bad colour", settings("RED", "Arial")).andExpect(status().isBadRequest());
        create("Bad font", settings("004B87", "Comic Sans MS")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not available")));
        mvc.perform(post("/api/admin/themes").with(csrf()).with(user("e").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"settings\":{}}")).andExpect(status().isForbidden());

        long id = json.readTree(create("Quarterly review", settings("004B87", "Georgia")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.settings.backgrounds.title.color").value("004B87")) // defaults follow the primary colour
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        update(id, settings("00594F", "Georgia"), 1).andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(2));
        update(id, settings("111111", "Georgia"), 1).andExpect(status().isConflict());
        create("quarterly REVIEW", settings("004B87", "Arial")).andExpect(status().isBadRequest());
    }

    @Test
    void backgroundsAreCroppedTo16by9AndReachTheMergedTheme() throws Exception {
        BufferedImage square = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(square, "png", png);
        long img = json.readTree(mvc.perform(multipart("/api/admin/theme-images")
                        .file(new MockMultipartFile("file", "bg.png", "image/png", png.toByteArray())).param("kind", "background")
                        .with(csrf()).with(user("th-admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        byte[] stored = mvc.perform(get("/api/theme-images/" + img).with(user("r").roles("END_USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(back.getWidth()).isEqualTo(400);
        assertThat(back.getHeight()).isEqualTo(225);

        mvc.perform(multipart("/api/admin/theme-images").file(new MockMultipartFile("file", "x.gif", "image/gif", "GIF89a".getBytes()))
                .param("kind", "background").with(csrf()).with(user("th-admin").roles("ADMIN"))).andExpect(status().isBadRequest());

        ObjectNode s = settings("004B87", "Arial");
        s.putObject("backgrounds").putObject("title").put("imageId", img);
        mvc.perform(post("/api/admin/themes/preview").with(csrf()).with(user("th-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(s)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layouts.IAM_TITLE.backgroundImage").value("api/theme-images/" + img))
                .andExpect(jsonPath("$.colors.accent1").value("004B87"))
                .andExpect(jsonPath("$.bodyFont").value("Calibri"));
    }

    @Test
    void newMeetingsTakeTheDefaultTheme() throws Exception {
        long id = json.readTree(create("Default swap test", settings("5B2C83", "Verdana")).andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode all = json.readTree(mvc.perform(get("/api/themes").with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        long red = 0;
        for (JsonNode t : all) if (t.get("name").asText().equals("IAM Red")) red = t.get("id").asLong();
        // A Meeting takes the default Theme when it is created (ticket 31), so switch the default first.
        mvc.perform(post("/api/admin/themes/" + id + "/default").with(csrf()).with(user("th-admin").roles("ADMIN"))).andExpect(status().isNoContent());
        try {
            long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("th-admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2051-01\",\"meetingDate\":\"2051-01-30\"}"))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(get("/api/meetings/" + m + "/theme").with(user("r").roles("END_USER")))
                    .andExpect(jsonPath("$.colors.accent1").value("5B2C83"))
                    .andExpect(jsonPath("$.headFont").value("Verdana"));
        } finally {
            mvc.perform(post("/api/admin/themes/" + red + "/default").with(csrf()).with(user("th-admin").roles("ADMIN"))).andExpect(status().isNoContent());
        }
    }
}
