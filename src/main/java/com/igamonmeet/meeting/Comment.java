package com.igamonmeet.meeting;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "meeting_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long meetingId;
    private String sectionKey;
    private Long parentId;
    private String author;
    private String body;
    private Instant createdAt;
    private boolean resolved;
    private String resolvedBy;
    private Instant resolvedAt;

    protected Comment() {
    }

    Comment(Long meetingId, String sectionKey, Long parentId, String author, String body, Instant createdAt) {
        this.meetingId = meetingId;
        this.sectionKey = sectionKey;
        this.parentId = parentId;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    void resolve(boolean value, String by, Instant at) {
        this.resolved = value;
        this.resolvedBy = value ? by : null;
        this.resolvedAt = value ? at : null;
    }

    public Long getId() { return id; }
    public Long getMeetingId() { return meetingId; }
    public String getSectionKey() { return sectionKey; }
    public Long getParentId() { return parentId; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isResolved() { return resolved; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
}
