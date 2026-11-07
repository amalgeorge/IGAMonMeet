package com.igamonmeet.audit;

import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** The single writer every feature uses to record what happened (docs/spec.md §7.2). Never pass secrets. */
@Service
public class AuditLog {

    public static final String SIGN_IN = "SIGN_IN";
    public static final String SIGN_IN_FAILED = "SIGN_IN_FAILED";
    public static final String LOCKED_OUT = "LOCKED_OUT";
    public static final String REGISTERED = "REGISTERED";
    public static final String APPROVED = "APPROVED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REJECTED = "REJECTED";
    public static final String DISABLED = "DISABLED";
    public static final String ENABLED = "ENABLED";
    public static final String UNLOCKED = "UNLOCKED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String RESET_REQUESTED = "RESET_REQUESTED";

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditLog(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Records an event by the signed-in user. */
    public void record(String action, String targetType, Object targetId, String detail) {
        recordAs(currentUser(), action, targetType, targetId, detail);
    }

    /** Records an event with an explicit actor, e.g. a sign-in attempt before anyone is signed in. */
    public void recordAs(String actor, String action, String targetType, Object targetId, String detail) {
        save(actor, action, targetType, targetId, null, null, null, detail);
    }

    /** Records one field's change by the signed-in user. */
    public void recordChange(String targetType, Object targetId, String field, Object oldValue, Object newValue) {
        save(currentUser(), "CHANGED", targetType, targetId, field, str(oldValue), str(newValue), null);
    }

    private void save(String actor, String action, String targetType, Object targetId,
                      String field, String oldValue, String newValue, String detail) {
        repository.save(new AuditEntry(clock.instant(), cut(actor, 100), action, targetType,
                cut(str(targetId), 100), field, cut(oldValue, 4000), cut(newValue, 4000), cut(detail, 1000)));
    }

    private static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || !a.isAuthenticated() ? null : a.getName();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
