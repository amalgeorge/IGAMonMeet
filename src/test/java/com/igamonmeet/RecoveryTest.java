package com.igamonmeet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.igamonmeet.account.AppUser;
import com.igamonmeet.account.AppUserRepository;
import com.igamonmeet.account.RecoveryService;
import com.igamonmeet.account.RecoveryToken;
import com.igamonmeet.account.RecoveryTokenRepository;
import com.igamonmeet.account.Role;
import com.igamonmeet.account.UserStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RecoveryTest {

    private static final String NEW_PW = "recovered-password-1";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RecoveryTokenRepository tokens;

    /** Mirrors what usp_AdminRecovery stores: an Admin with an unusable hash plus a hashed token. */
    private String issue(String username, Duration validFor) {
        AppUser admin = users.findByUsernameIgnoreCase(username).orElseGet(() ->
                users.save(new AppUser(username, username, "", "!", Role.ADMIN, UserStatus.ACTIVE, Instant.now())));
        String token = Long.toHexString(System.nanoTime()).toUpperCase() + "ABCDEF0123456789";
        tokens.save(new RecoveryToken(admin.getId(), RecoveryService.hash(token), Instant.now(), Instant.now().plus(validFor)));
        return token;
    }

    private ResultActions redeem(String token, String pw) throws Exception {
        return mvc.perform(post("/api/auth/recover").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + pw + "\",\"confirmPassword\":\"" + pw + "\"}"));
    }

    @Test
    void firstAdminSetsPasswordThenSignsIn() throws Exception {
        String token = issue("first-admin", Duration.ofHours(1));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "first-admin").param("password", "!"))
                .andExpect(status().isUnauthorized());
        redeem(token, NEW_PW).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "first-admin").param("password", NEW_PW))
                .andExpect(status().isOk());
    }

    @Test
    void tokenWorksOnlyOnce() throws Exception {
        String token = issue("once-admin", Duration.ofHours(1));
        redeem(token, NEW_PW).andExpect(status().isNoContent());
        redeem(token, "another-password-9").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void expiredAndUnknownTokensFail() throws Exception {
        String token = issue("late-admin", Duration.ofSeconds(-1));
        redeem(token, NEW_PW).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        redeem("0000DEADBEEF", NEW_PW).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void passwordRulesStillApply() throws Exception {
        String token = issue("weak-admin", Duration.ofHours(1));
        redeem(token, "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_LENGTH"));
        redeem(token, NEW_PW).andExpect(status().isNoContent());
    }
}
