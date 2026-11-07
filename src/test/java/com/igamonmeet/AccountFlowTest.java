package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.igamonmeet.account.AppUser;
import com.igamonmeet.account.AppUserRepository;
import com.igamonmeet.account.Role;
import com.igamonmeet.account.UserStatus;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AccountFlowTest {

    private static final String PW = "correct-horse-battery";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    private static String newName() {
        return "user" + SEQ.incrementAndGet();
    }

    private ResultActions register(String username, String password, String confirm) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"fullName\":\"Test User\",\"email\":\"%s@example.com\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}",
                username, username, password, confirm);
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password));
    }

    private void approve(String username) throws Exception {
        long id = users.findByUsernameIgnoreCase(username).orElseThrow().getId();
        mvc.perform(post("/api/admin/users/" + id + "/approve").with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void registrationCreatesPendingEndUser() throws Exception {
        String name = newName();
        register(name, PW, PW).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        AppUser u = users.findByUsernameIgnoreCase(name).orElseThrow();
        assertThat(u.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(u.getRole()).isEqualTo(Role.END_USER);
        assertThat(u.getPasswordHash()).startsWith("$2a$12$").doesNotContain(PW);
    }

    @Test
    void registrationRulesAreEnforced() throws Exception {
        String name = newName();
        register(name, "short", "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_LENGTH"));
        register(name, PW, PW + "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_MISMATCH"));
        register(name, PW, PW).andExpect(status().isCreated());
        register(name.toUpperCase(), PW, PW).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
    }

    @Test
    void registrationNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingUserIsToldOnlyWithCorrectPassword() throws Exception {
        String name = newName();
        register(name, PW, PW);
        login(name, "wrong-password-123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("BAD_CREDENTIALS"));
        login(name, PW).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("PENDING"));
    }

    @Test
    void unknownUserLooksLikeWrongPassword() throws Exception {
        login("nobody-here", PW).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("BAD_CREDENTIALS"));
    }

    @Test
    void approvedUserSignsInAndGetsSession() throws Exception {
        String name = newName();
        register(name, PW, PW);
        approve(name);
        MvcResult result = login(name, PW).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.role").value("END_USER"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        String name = newName();
        register(name, PW, PW);
        approve(name);
        for (int i = 0; i < 5; i++) {
            login(name, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        assertThat(users.findByUsernameIgnoreCase(name).orElseThrow().isLockedAt(Instant.now())).isTrue();
        login(name, PW).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("BAD_CREDENTIALS"));
    }

    @Test
    void endUserCannotApprove() throws Exception {
        mvc.perform(post("/api/admin/users/1/approve").with(csrf()).with(user("someone").roles("END_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastFivePasswordsCannotBeReused() throws Exception {
        String name = newName();
        register(name, PW, PW);
        approve(name);
        String current = PW;
        for (int i = 1; i <= 4; i++) {
            String next = "another-password-" + i;
            changePassword(name, current, next).andExpect(status().isNoContent());
            current = next;
        }
        // PW is the 5th most recent: still blocked.
        changePassword(name, current, PW).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_REUSED"));
        changePassword(name, current, "another-password-5").andExpect(status().isNoContent());
        // Now PW is 6th most recent: allowed again.
        changePassword(name, "another-password-5", PW).andExpect(status().isNoContent());
    }

    private ResultActions changePassword(String name, String current, String next) throws Exception {
        String body = String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}", current, next, next);
        return mvc.perform(post("/api/account/password").with(csrf()).with(user(name).roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
