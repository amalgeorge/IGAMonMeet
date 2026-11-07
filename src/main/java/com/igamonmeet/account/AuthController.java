package com.igamonmeet.account;

import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in itself is POST /api/auth/login (form fields username, password), handled by Spring Security. */
@RestController
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,100}") String username,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password,
            @NotBlank String confirmPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword, @NotBlank String confirmPassword) {
    }

    public record ForgotRequest(@NotBlank String username) {
    }

    public record RecoverRequest(@NotBlank String token, @NotBlank String newPassword, @NotBlank String confirmPassword) {
    }

    public record Me(String username, String fullName, String role, boolean mustChangePassword) {
    }

    private final AccountService accounts;
    private final AppUserRepository users;
    private final AdminUserService admin;
    private final RecoveryService recovery;

    public AuthController(AccountService accounts, AppUserRepository users, AdminUserService admin, RecoveryService recovery) {
        this.admin = admin;
        this.recovery = recovery;
        this.accounts = accounts;
        this.users = users;
    }

    /** The React app calls this first; the token also arrives in the XSRF-TOKEN cookie. */
    @GetMapping("/api/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest r) {
        return Map.of("status", accounts.register(r.username(), r.fullName(), r.email(), r.password(), r.confirmPassword()).getStatus().name());
    }

    /** Always 202, whether or not the username exists, so it cannot be used to find accounts. */
    @PostMapping("/api/auth/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody ForgotRequest r) {
        admin.requestReset(r.username());
    }

    /** Completes a DBA-issued recovery link (token from the URL fragment). */
    @PostMapping("/api/auth/recover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recover(@Valid @RequestBody RecoverRequest r) {
        recovery.redeem(r.token(), r.newPassword(), r.confirmPassword());
    }

    @GetMapping("/api/auth/me")
    public Me me(Authentication auth) {
        AppUser u = users.findByUsernameIgnoreCase(auth.getName()).orElseThrow();
        return new Me(u.getUsername(), u.getFullName(), u.getRole().name(), u.isMustChangePassword());
    }

    @PostMapping("/api/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest r, Authentication auth) {
        accounts.changePassword(auth.getName(), r.currentPassword(), r.newPassword(), r.confirmPassword());
    }

}
