package com.igamonmeet.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import com.igamonmeet.audit.AuditLog;
import com.igamonmeet.settings.FeatureToggle;
import com.igamonmeet.settings.Settings;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, approval, password rules and sign-in bookkeeping (docs/spec.md §1). */
@Service
public class AccountService {

    public static final int MIN_PASSWORD_LENGTH = 12;
    /** BCrypt only uses the first 72 bytes; 64 characters keeps every password fully significant. */
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int REUSE_DEPTH = 5;
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final PasswordHistoryRepository history;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final AuditLog audit;
    private final Settings settings;

    public AccountService(AppUserRepository users, PasswordHistoryRepository history, PasswordEncoder encoder, Clock clock, AuditLog audit,
                          Settings settings) {
        this.audit = audit;
        this.settings = settings;
        this.users = users;
        this.history = history;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Creates a Pending user; an Admin must approve before sign-in works. */
    @Transactional
    public AppUser register(String username, String fullName, String email, String password, String confirmPassword) {
        if (!settings.isOn(FeatureToggle.SELF_REGISTRATION)) {
            throw new AccountException(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED", "Registration is closed. Ask an Admin for an account.");
        }
        String name = username.trim();
        if (users.existsByUsernameIgnoreCase(name)) {
            throw new AccountException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is already taken.");
        }
        checkNewPassword(password, confirmPassword);
        AppUser user = users.save(new AppUser(name, fullName.trim(), email.trim(), encoder.encode(password),
                Role.END_USER, UserStatus.PENDING, clock.instant()));
        history.save(new PasswordHistory(user.getId(), user.getPasswordHash(), clock.instant()));
        boolean approval = settings.isOn(FeatureToggle.REGISTRATION_APPROVAL);
        if (!approval) {
            user.setStatus(UserStatus.ACTIVE);
        }
        audit.recordAs(user.getUsername(), AuditLog.REGISTERED, "User", user.getId(), approval ? null : "Auto-approved (approval step off)");
        return user;
    }

    @Transactional
    public AppUser approve(long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
        if (user.getStatus() != UserStatus.PENDING) {
            throw new AccountException(HttpStatus.CONFLICT, "NOT_PENDING", "Only pending users can be approved.");
        }
        user.setStatus(UserStatus.ACTIVE);
        audit.record(AuditLog.APPROVED, "User", user.getId(), user.getUsername());
        return user;
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword, String confirmPassword) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Current password is incorrect.");
        }
        setNewPassword(user, newPassword, confirmPassword);
        audit.recordAs(user.getUsername(), AuditLog.PASSWORD_CHANGED, "User", user.getId(), null);
    }

    /** Applies the password rules (length, confirmation, last 5) and stores the new hash. */
    @Transactional
    public void setNewPassword(AppUser user, String newPassword, String confirmPassword) {
        checkNewPassword(newPassword, confirmPassword);
        boolean reused = history.findTop5ByUserIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .anyMatch(h -> encoder.matches(newPassword, h.getPasswordHash()));
        if (reused) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSED",
                    "Choose a password you have not used for your last " + REUSE_DEPTH + " passwords.");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        history.save(new PasswordHistory(user.getId(), user.getPasswordHash(), clock.instant()));
    }

    /** Counts a wrong password; the 5th in a row locks the account for 15 minutes. */
    @Transactional
    public void recordFailedSignIn(long userId) {
        users.findById(userId).ifPresent(user -> {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(clock.instant().plus(LOCKOUT));
                attempts = 0;
                audit.recordAs(user.getUsername(), AuditLog.LOCKED_OUT, "User", user.getId(), "Locked for " + LOCKOUT.toMinutes() + " minutes");
            }
            user.setFailedAttempts(attempts);
        });
    }

    @Transactional
    public void recordSuccessfulSignIn(long userId) {
        users.findById(userId).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        });
    }

    public Instant now() {
        return clock.instant();
    }

    private static void checkNewPassword(String password, String confirmPassword) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "PASSWORD_LENGTH",
                    "Password must be " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters.");
        }
        if (!password.equals(confirmPassword)) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Passwords do not match.");
        }
    }
}
