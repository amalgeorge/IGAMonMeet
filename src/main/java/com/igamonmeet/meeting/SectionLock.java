package com.igamonmeet.meeting;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "section_lock")
public class SectionLock {

    @Id
    private Long sectionId;

    private String username;
    private Instant acquiredAt;
    private Instant lastSeenAt;

    protected SectionLock() {
    }

    SectionLock(Long sectionId, String username, Instant now) {
        this.sectionId = sectionId;
        this.username = username;
        this.acquiredAt = now;
        this.lastSeenAt = now;
    }

    boolean isLiveAt(Instant now) {
        return lastSeenAt.plus(SectionEditor.LOCK_IDLE).isAfter(now);
    }

    boolean isHeldBy(String user) {
        return username.equalsIgnoreCase(user);
    }

    void takeOver(String user, Instant now) {
        this.username = user;
        this.acquiredAt = now;
        this.lastSeenAt = now;
    }

    void seen(Instant now) {
        this.lastSeenAt = now;
    }

    public String getUsername() { return username; }
}
