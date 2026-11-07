package com.igamonmeet.account;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: everything under /api/admin/** requires ROLE_ADMIN (SecurityConfig). */
@RestController
public class AdminUserController {

    public record UserRow(long id, String username, String fullName, String email, String role, String status,
                          boolean locked, Instant createdAt) {
    }

    public record ResetRow(long id, long userId, String username, String fullName, Instant requestedAt) {
    }

    public record RoleChange(Role role) {
    }

    private final AppUserRepository users;
    private final PasswordResetRequestRepository resets;
    private final AccountService accounts;
    private final AdminUserService admin;

    public AdminUserController(AppUserRepository users, PasswordResetRequestRepository resets, AccountService accounts, AdminUserService admin) {
        this.users = users;
        this.resets = resets;
        this.accounts = accounts;
        this.admin = admin;
    }

    @GetMapping("/api/admin/users")
    public List<UserRow> list(@RequestParam(required = false) UserStatus status) {
        List<AppUser> found = status == null ? users.findAllByOrderByUsername() : users.findByStatusOrderByCreatedAt(status);
        return found.stream().map(this::row).collect(Collectors.toList());
    }

    /** What is waiting on an Admin: pending registrations and open reset requests. */
    @GetMapping("/api/admin/queue")
    public Map<String, Object> queue() {
        List<ResetRow> requests = resets.findByStatusOrderByRequestedAt(PasswordResetRequest.OPEN).stream()
                .map(r -> users.findById(r.getUserId()).map(u -> new ResetRow(r.getId(), u.getId(), u.getUsername(), u.getFullName(), r.getRequestedAt())))
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toList());
        List<UserRow> pending = users.findByStatusOrderByCreatedAt(UserStatus.PENDING).stream().map(this::row).collect(Collectors.toList());
        return Map.of("pendingUsers", pending, "resetRequests", requests, "count", pending.size() + requests.size());
    }

    @PostMapping("/api/admin/users/{id}/approve")
    public Map<String, String> approve(@PathVariable long id) {
        return Map.of("status", accounts.approve(id).getStatus().name());
    }

    @PostMapping("/api/admin/users/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable long id) {
        admin.reject(id);
    }

    @PostMapping("/api/admin/users/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable long id, Authentication auth) {
        admin.disable(id, auth.getName());
    }

    @PostMapping("/api/admin/users/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable long id) {
        admin.enable(id);
    }

    @PostMapping("/api/admin/users/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable long id) {
        admin.unlock(id);
    }

    @PostMapping("/api/admin/users/{id}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void role(@PathVariable long id, @RequestBody RoleChange change, Authentication auth) {
        admin.changeRole(id, change.role(), auth.getName());
    }

    /** The temporary password is in this response only; show it once and never log it. */
    @PostMapping("/api/admin/users/{id}/reset-password")
    public Map<String, String> resetPassword(@PathVariable long id, Authentication auth) {
        return Map.of("temporaryPassword", admin.resetPassword(id, auth.getName()));
    }

    private UserRow row(AppUser u) {
        return new UserRow(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole().name(), u.getStatus().name(),
                u.isLockedAt(accounts.now()), u.getCreatedAt());
    }
}
