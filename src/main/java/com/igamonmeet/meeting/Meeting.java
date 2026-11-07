package com.igamonmeet.meeting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "meeting")
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_month")
    private String month;

    private LocalDate meetingDate;
    private String title;
    private int templateVersionNo;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<MeetingSection> sections = new ArrayList<>();

    protected Meeting() {
    }

    Meeting(String month, LocalDate meetingDate, String title, int templateVersionNo, String createdBy, Instant now) {
        this.month = month;
        this.meetingDate = meetingDate;
        this.title = title;
        this.templateVersionNo = templateVersionNo;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void add(MeetingSection s) {
        sections.add(s);
    }

    void softDelete(String by, Instant at) {
        this.deletedAt = at;
        this.deletedBy = by;
    }

    void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public void touch(Instant at) {
        this.updatedAt = at;
    }

    public Long getId() { return id; }
    public String getMonth() { return month; }
    public LocalDate getMeetingDate() { return meetingDate; }
    public String getTitle() { return title; }
    public int getTemplateVersionNo() { return templateVersionNo; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public List<MeetingSection> getSections() { return sections; }
}
