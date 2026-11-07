package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.account.AppUser;
import com.igamonmeet.account.AppUserRepository;
import com.igamonmeet.account.PasswordResetRequestRepository;
import com.igamonmeet.account.Role;
import com.igamonmeet.account.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUsersTest {

    private static final String PW = "correct-horse-battery";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordResetRequestRepository resets;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private AppUser active(String name, Role role) {
        return users.save(new AppUser(name, "Full " + name, name + "@example.com", encoder.encode(PW), role, UserStatus.ACTIVE, Instant.now()));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.with(csrf()).with(user("root-admin").roles("ADMIN"));
    }

    private MockHttpSession signIn(String name, String pw) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", name).param("password", pw))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test
    void forgotPasswordNeverRevealsWhetherUserExists() throws Exception {
        long before = resets.count();
        mvc.perform(post("/api/auth/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ghost-user\"}"))
                .andExpect(status().isAccepted());
        assertThat(resets.count()).isEqualTo(before);
    }

    @Test
    void resetFlowForcesPasswordChange() throws Exception {
        AppUser u = active("forgetful1", Role.ADMIN);
        mvc.perform(post("/api/auth/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"forgetful1\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(asAdmin(get("/api/admin/queue"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.resetRequests[?(@.username=='forgetful1')]").exists());

        String body = mvc.perform(asAdmin(post("/api/admin/users/" + u.getId() + "/reset-password")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String temp = json.readTree(body).get("temporaryPassword").asText();
        assertThat(temp).hasSize(16);
        mvc.perform(asAdmin(get("/api/admin/queue"))).andExpect(jsonPath("$.resetRequests[?(@.username=='forgetful1')]").doesNotExist());

        MockHttpSession s = signIn("forgetful1", temp);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/admin/queue").session(s)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/account/password").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + temp + "\",\"newPassword\":\"brand-new-password-1\",\"confirmPassword\":\"brand-new-password-1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/queue").session(s)).andExpect(status().isOk());
    }

    @Test
    void disabledUserLosesSessionImmediately() throws Exception {
        AppUser u = active("leaver1", Role.END_USER);
        MockHttpSession s = signIn("leaver1", PW);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk());
        mvc.perform(asAdmin(post("/api/admin/users/" + u.getId() + "/disable"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
        login("leaver1").andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("DISABLED"));
    }

    @Test
    void adminCannotDisableSelf() throws Exception {
        AppUser me = active("selfish-admin", Role.ADMIN);
        active("other-admin-1", Role.ADMIN);
        mvc.perform(post("/api/admin/users/" + me.getId() + "/disable").with(csrf()).with(user("selfish-admin").roles("ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SELF"));
    }

    @Test
    void lastActiveAdminCannotBeDemoted() throws Exception {
        AppUser keep = active("last-admin", Role.ADMIN);
        users.findAll().stream()
                .filter(a -> a.getRole() == Role.ADMIN && a.getStatus() == UserStatus.ACTIVE && !a.getId().equals(keep.getId()))
                .forEach(a -> { a.setStatus(UserStatus.DISABLED); users.save(a); });
        mvc.perform(asAdmin(post("/api/admin/users/" + keep.getId() + "/role")).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"END_USER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LAST_ADMIN"));
    }

    @Test
    void rejectAndUnlockWork() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"username\":\"applicant1\",\"fullName\":\"A\",\"email\":\"a@example.com\",\"password\":\"" + PW + "\",\"confirmPassword\":\"" + PW + "\"}"));
        long id = users.findByUsernameIgnoreCase("applicant1").orElseThrow().getId();
        mvc.perform(asAdmin(post("/api/admin/users/" + id + "/reject"))).andExpect(status().isNoContent());
        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo(UserStatus.REJECTED);

        AppUser locked = active("locked1", Role.END_USER);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).param("username", "locked1").param("password", "wrong-password-" + i));
        }
        login("locked1").andExpect(status().isUnauthorized());
        mvc.perform(asAdmin(post("/api/admin/users/" + locked.getId() + "/unlock"))).andExpect(status().isNoContent());
        login("locked1").andExpect(status().isOk());
    }

    @Test
    void endUserCannotManageUsers() throws Exception {
        mvc.perform(get("/api/admin/users").with(user("plain").roles("END_USER"))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions login(String name) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", name).param("password", PW));
    }
}
