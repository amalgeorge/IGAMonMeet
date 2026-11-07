package com.igamonmeet.meeting;

import java.time.Instant;

/** Someone saved the Section after this editor loaded it. Carries their version so the user can choose. */
public class VersionConflictException extends RuntimeException {

    public record Current(long version, String content, String updatedBy, Instant updatedAt) {
    }

    private final Current current;

    public VersionConflictException(Current current) {
        super("Someone else saved this section while you were editing.");
        this.current = current;
    }

    public Current getCurrent() { return current; }
}
