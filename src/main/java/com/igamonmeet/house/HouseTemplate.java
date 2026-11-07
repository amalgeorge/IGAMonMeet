package com.igamonmeet.house;

import java.time.Instant;
import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "house_template")
public class HouseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int versionNo;
    private String fileName;

    @Basic(fetch = FetchType.LAZY)
    private byte[] content;

    private String themeJson;
    private String uploadedBy;
    private Instant uploadedAt;

    protected HouseTemplate() {
    }

    HouseTemplate(int versionNo, String fileName, byte[] content, String themeJson, String uploadedBy, Instant uploadedAt) {
        this.versionNo = versionNo;
        this.fileName = fileName;
        this.content = content;
        this.themeJson = themeJson;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public int getVersionNo() { return versionNo; }
    public String getFileName() { return fileName; }
    public byte[] getContent() { return content; }
    public String getThemeJson() { return themeJson; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
