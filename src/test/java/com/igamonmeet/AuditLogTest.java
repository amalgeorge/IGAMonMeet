package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.igamonmeet.account.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogTest {

    private static final String PW = "correct-horse-battery";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    @Test
    void accountEventsAreRecordedWithoutSecrets() throws Exception {
        String name = "audited1";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"username\":\"" + name + "\",\"fullName\":\"A\",\"email\":\"a@example.com\",\"password\":\"" + PW + "\",\"confirmPassword\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        long id = users.findByUsernameIgnoreCase(name).orElseThrow().getId();
        mvc.perform(post("/api/admin/users/" + id + "/approve").with(csrf()).with(user("boss").roles("ADMIN")));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", name).param("password", "wrong-password-1"));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", name).param("password", PW));

        mvc.perform(get("/api/admin/audit").param("actor", name).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("SIGN_IN"))
                .andExpect(jsonPath("$.items[1].action").value("SIGN_IN_FAILED"))
                .andExpect(jsonPath("$.items[1].detail").value("WRONG_PASSWORD"))
                .andExpect(jsonPath("$.items[2].action").value("REGISTERED"));

        mvc.perform(get("/api/admin/audit").param("action", "APPROVED").param("actor", "boss").with(user("boss").roles("ADMIN")))
                .andExpect(jsonPath("$.items[0].detail").value(name));

        String csv = mvc.perform(get("/api/admin/audit.csv").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("SIGN_IN_FAILED").doesNotContain(PW).doesNotContain("wrong-password-1");
    }

    @Test
    void endUserCannotReadAuditLog() throws Exception {
        mvc.perform(get("/api/admin/audit").with(user("someone").roles("END_USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit.csv").with(user("someone").roles("END_USER"))).andExpect(status().isForbidden());
    }

    @Test
    void csvCellsNeutraliseFormulas() {
        assertThat(com.igamonmeet.audit.AuditControllerAccess.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(com.igamonmeet.audit.AuditControllerAccess.cell("plain")).isEqualTo("\"plain\"");
    }
}
