package com.igamonmeet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class SettingsTest {

    private static final String PW = "correct-horse-battery";

    @Autowired MockMvc mvc;

    private ResultActions toggle(String key, boolean on) throws Exception {
        return mvc.perform(put("/api/admin/settings/" + key).with(csrf()).with(user("settings-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + on + "}"));
    }

    private ResultActions register(String name) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"username\":\"" + name + "\",\"fullName\":\"A\",\"email\":\"a@example.com\",\"password\":\"" + PW + "\",\"confirmPassword\":\"" + PW + "\"}"));
    }

    /** The database is shared by all test classes: always switch everything back on. */
    @AfterEach
    void restore() throws Exception {
        for (String k : new String[] {"SELF_REGISTRATION", "REGISTRATION_APPROVAL", "FORGOT_PASSWORD", "QUEUE_BADGE"}) {
            toggle(k, true);
        }
    }

    @Test
    void registrationCanBeClosed() throws Exception {
        toggle("SELF_REGISTRATION", false).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/features")).andExpect(jsonPath("$.selfRegistration").value(false));
        register("closed1").andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("REGISTRATION_CLOSED"));
    }

    @Test
    void approvalOffActivatesNewAccounts() throws Exception {
        toggle("REGISTRATION_APPROVAL", false).andExpect(status().isNoContent());
        register("autoapproved1").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "autoapproved1").param("password", PW))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordCanBeSwitchedOff() throws Exception {
        toggle("FORGOT_PASSWORD", false);
        mvc.perform(post("/api/auth/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FEATURE_OFF"));
    }

    @Test
    void changesAreAudited() throws Exception {
        toggle("QUEUE_BADGE", false);
        mvc.perform(get("/api/admin/audit").param("actor", "settings-admin").with(user("auditor").roles("ADMIN")))
                .andExpect(jsonPath("$.items[0].targetId").value("QUEUE_BADGE"))
                .andExpect(jsonPath("$.items[0].oldValue").value("true"))
                .andExpect(jsonPath("$.items[0].newValue").value("false"));
    }

    @Test
    void endUserCannotChangeSettings() throws Exception {
        mvc.perform(put("/api/admin/settings/QUEUE_BADGE").with(csrf()).with(user("plain").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }
}
