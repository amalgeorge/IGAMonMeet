package com.igamonmeet.account;

import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.settings.FeatureToggle;
import com.igamonmeet.settings.Settings;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin actions on Users (docs/spec.md §1.7, §1.10). Every action is audit-logged. */
@Service
public class AdminUserService {

    /** No 0/O, 1/l/I: temporary passwords are read out or typed by hand. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_LENGTH = 16;

    private final AppUserRepository users;
    private final PasswordHistoryRepository history;
    private final PasswordResetRequestRepository resets;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final Clock clock;
    private final Settings settings;
    private final SecureRandom random = new SecureRandom();

    public AdminUserService(AppUserRepository users, PasswordHistoryRepository history, PasswordResetRequestRepository resets,
                            PasswordEncoder encoder, AuditLog audit, Clock clock, Settings settings) {
        this.settings = settings;
        this.users = users;
        this.history = history;
        this.resets = resets;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void reject(long id) {
        AppUser u = find(id);
        require(u.getStatus() == UserStatus.PENDING, "NOT_PENDING", "Only pending users can be rejected.");
        u.setStatus(UserStatus.REJECTED);
        audit.record(AuditLog.REJECTED, "User", id, u.getUsername());
    }

    @Transactional
    public void disable(long id, String actingAdmin) {
        AppUser u = find(id);
        require(u.getStatus() == UserStatus.ACTIVE, "NOT_ACTIVE", "Only active users can be disabled.");
        notSelf(u, actingAdmin);
        notLastAdmin(u);
        u.setStatus(UserStatus.DISABLED);
        audit.record(AuditLog.DISABLED, "User", id, u.getUsername());
    }

    @Transactional
    public void enable(long id) {
        AppUser u = find(id);
        require(u.getStatus() == UserStatus.DISABLED, "NOT_DISABLED", "Only disabled users can be enabled.");
        u.setStatus(UserStatus.ACTIVE);
        audit.record(AuditLog.ENABLED, "User", id, u.getUsername());
    }

    @Transactional
    public void unlock(long id) {
        AppUser u = find(id);
        u.setLockedUntil(null);
        u.setFailedAttempts(0);
        audit.record(AuditLog.UNLOCKED, "User", id, u.getUsername());
    }

    @Transactional
    public void changeRole(long id, Role role, String actingAdmin) {
        AppUser u = find(id);
        if (u.getRole() == role) {
            return;
        }
        if (role != Role.ADMIN) {
            notSelf(u, actingAdmin);
            notLastAdmin(u);
        }
        Role old = u.getRole();
        u.setRole(role);
        audit.recordChange("User", id, "role", old, role);
    }

    /** Sets a one-time temporary password, returned once and never stored in clear. */
    @Transactional
    public String resetPassword(long id, String actingAdmin) {
        AppUser u = find(id);
        String temp = temporaryPassword();
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setLockedUntil(null);
        u.setFailedAttempts(0);
        history.save(new PasswordHistory(u.getId(), u.getPasswordHash(), clock.instant()));
        resets.findByUserIdAndStatus(u.getId(), PasswordResetRequest.OPEN).forEach(r -> r.close(actingAdmin, clock.instant()));
        audit.record(AuditLog.PASSWORD_RESET, "User", id, u.getUsername());
        return temp;
    }

    /** "Forgot password?": queues a request for active users only, and reveals nothing either way. */
    @Transactional
    public void requestReset(String username) {
        if (!settings.isOn(FeatureToggle.FORGOT_PASSWORD)) {
            throw new AccountException(HttpStatus.FORBIDDEN, "FEATURE_OFF", "Password reset requests are switched off. Contact an Admin.");
        }
        users.findByUsernameIgnoreCase(username.trim())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> resets.findByUserIdAndStatus(u.getId(), PasswordResetRequest.OPEN).isEmpty())
                .ifPresent(u -> {
                    resets.save(new PasswordResetRequest(u.getId(), clock.instant()));
                    audit.recordAs(u.getUsername(), AuditLog.RESET_REQUESTED, "User", u.getId(), null);
                });
    }

    private AppUser find(long id) {
        return users.findById(id).orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    }

    private void notSelf(AppUser u, String actingAdmin) {
        require(!u.getUsername().equalsIgnoreCase(actingAdmin), "SELF", "You cannot disable or demote your own account.");
    }

    private void notLastAdmin(AppUser u) {
        boolean lastAdmin = u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE
                && users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1;
        require(!lastAdmin, "LAST_ADMIN", "At least one active Admin must remain.");
    }

    private static void require(boolean ok, String code, String message) {
        if (!ok) {
            throw new AccountException(HttpStatus.CONFLICT, code, message);
        }
    }

    private String temporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_LENGTH);
        for (int i = 0; i < TEMP_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
