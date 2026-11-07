package com.igamonmeet.meeting;

import com.igamonmeet.template.TeamTag;
import java.time.Instant;
import java.time.LocalDate;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "action_item")
public class ActionItem {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long meetingId;
    private String title;
    private String owner;

    @Enumerated(EnumType.STRING)
    private TeamTag teamTag;

    private LocalDate dueDate;
    private String status;
    /** The Meeting where this item was first agreed; set on carried-forward copies. */
    private Long originMeetingId;
    private int sortOrder;
    private Instant createdAt;

    @Version
    private long version;

    protected ActionItem() {
    }

    public ActionItem(Long meetingId, String title, String owner, TeamTag teamTag, LocalDate dueDate, String status,
                      Long originMeetingId, int sortOrder, Instant createdAt) {
        this.meetingId = meetingId;
        this.title = title;
        this.owner = owner;
        this.teamTag = teamTag;
        this.dueDate = dueDate;
        this.status = status;
        this.originMeetingId = originMeetingId;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public void update(String title, String owner, TeamTag teamTag, LocalDate dueDate, String status) {
        this.title = title;
        this.owner = owner;
        this.teamTag = teamTag;
        this.dueDate = dueDate;
        this.status = status;
    }

    public long getVersion() { return version; }
    public Long getId() { return id; }
    public Long getMeetingId() { return meetingId; }
    public String getTitle() { return title; }
    public String getOwner() { return owner; }
    public TeamTag getTeamTag() { return teamTag; }
    public LocalDate getDueDate() { return dueDate; }
    public String getStatus() { return status; }
    public Long getOriginMeetingId() { return originMeetingId; }
    public int getSortOrder() { return sortOrder; }
}
