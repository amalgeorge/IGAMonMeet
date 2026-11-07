package com.igamonmeet.account;

import com.igamonmeet.audit.AuditLog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redeems a token from dbo.usp_AdminRecovery: the Admin sets their own password (ADR 0002). */
@Service
public class RecoveryService {

    private final RecoveryTokenRepository tokens;
    private final AppUserRepository users;
    private final AccountService accounts;
    private final AuditLog audit;
    private final Clock clock;

    public RecoveryService(RecoveryTokenRepository tokens, AppUserRepository users, AccountService accounts, AuditLog audit, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Same encoding as T-SQL: CONVERT(CHAR(64), HASHBYTES('SHA2_256', @varcharToken), 2). */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void redeem(String token, String newPassword, String confirmPassword) {
        Instant now = clock.instant();
        RecoveryToken t = tokens.findByTokenHash(hash(token.trim().toUpperCase()))
                .filter(x -> x.isUsableAt(now))
                .orElseThrow(() -> new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN",
                        "This recovery link is invalid, used or expired. Ask the DBA for a new one."));
        AppUser user = users.findById(t.getUserId())
                .filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "This recovery link is no longer valid."));
        accounts.setNewPassword(user, newPassword, confirmPassword);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        t.markUsed(now);
        audit.recordAs(user.getUsername(), "RECOVERY_COMPLETED", "User", user.getId(), null);
    }
}
