package com.igamonmeet.theme;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "theme_version")
public class ThemeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long themeId;
    private int versionNo;
    private String settingsJson;
    private String createdBy;
    private Instant createdAt;

    protected ThemeVersion() {
    }

    ThemeVersion(Long themeId, int versionNo, String settingsJson, String createdBy, Instant createdAt) {
        this.themeId = themeId;
        this.versionNo = versionNo;
        this.settingsJson = settingsJson;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getThemeId() { return themeId; }
    public int getVersionNo() { return versionNo; }
    public String getSettingsJson() { return settingsJson; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
