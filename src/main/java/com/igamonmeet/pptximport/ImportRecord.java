package com.igamonmeet.pptximport;

import java.time.Instant;
import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "import_record")
public class ImportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long meetingId;
    private String fileName;

    @Basic(fetch = FetchType.LAZY)
    private byte[] content;

    private int slideCount;
    private String uploadedBy;
    private Instant uploadedAt;
    private String appliedBy;
    private Instant appliedAt;
    private String result;

    protected ImportRecord() {
    }

    ImportRecord(Long meetingId, String fileName, byte[] content, int slideCount, String uploadedBy, Instant uploadedAt) {
        this.meetingId = meetingId;
        this.fileName = fileName;
        this.content = content;
        this.slideCount = slideCount;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    void applied(String by, Instant at, String result) {
        this.appliedBy = by;
        this.appliedAt = at;
        this.result = result.length() > 2000 ? result.substring(0, 2000) : result;
    }

    public Long getId() { return id; }
    public Long getMeetingId() { return meetingId; }
    public String getFileName() { return fileName; }
    public byte[] getContent() { return content; }
    public int getSlideCount() { return slideCount; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getAppliedBy() { return appliedBy; }
    public Instant getAppliedAt() { return appliedAt; }
    public String getResult() { return result; }
}
