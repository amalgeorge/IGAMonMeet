package com.igamonmeet.version;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "meeting_version")
public class MeetingVersion {

    public static final String IMPORT = "IMPORT";
    public static final String RESTORE = "RESTORE";
    public static final String SNAPSHOT = "SNAPSHOT";
    public static final String MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long meetingId;
    private int versionNo;
    private String reason;
    private String label;
    private String snapshotJson;
    private String createdBy;
    private Instant createdAt;

    protected MeetingVersion() {
    }

    MeetingVersion(Long meetingId, int versionNo, String reason, String label, String snapshotJson, String createdBy, Instant createdAt) {
        this.meetingId = meetingId;
        this.versionNo = versionNo;
        this.reason = reason;
        this.label = label;
        this.snapshotJson = snapshotJson;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getMeetingId() { return meetingId; }
    public int getVersionNo() { return versionNo; }
    public String getReason() { return reason; }
    public String getLabel() { return label; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
