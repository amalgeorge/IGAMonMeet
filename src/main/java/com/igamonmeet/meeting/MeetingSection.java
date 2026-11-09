package com.igamonmeet.meeting;

import com.igamonmeet.template.OverflowRule;
import com.igamonmeet.template.SectionKind;
import com.igamonmeet.template.TeamTag;
import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "meeting_section")
public class MeetingSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    private String sectionKey;
    private String name;

    @Enumerated(EnumType.STRING)
    private TeamTag teamTag;

    @Enumerated(EnumType.STRING)
    private SectionKind kind;

    private String layout;

    @Enumerated(EnumType.STRING)
    private OverflowRule overflowRule;

    private boolean required;

    @Column(name = "columns_spec")
    private String columnsSpec;

    private String chartKpiKey;
    private int sortOrder;

    private boolean onSlides = true;

    private Integer templateVersionNo;
    private boolean switchedOn;
    private boolean hidden;
    private String enabledKpis;

    /** Section content as JSON; its shape depends on the kind (ticket 09). */
    @Column(name = "content_json")
    private String contentJson;

    private String updatedBy;
    private Instant updatedAt;

    @Version
    private long version;

    protected MeetingSection() {
    }

    MeetingSection(Meeting meeting, String sectionKey, String name, TeamTag teamTag, SectionKind kind, String layout,
                   OverflowRule overflowRule, boolean required, String columnsSpec, String chartKpiKey, int sortOrder) {
        this.meeting = meeting;
        this.sectionKey = sectionKey;
        this.name = name;
        this.teamTag = teamTag;
        this.kind = kind;
        this.layout = layout;
        this.overflowRule = overflowRule;
        this.required = required;
        this.columnsSpec = columnsSpec;
        this.chartKpiKey = chartKpiKey;
        this.sortOrder = sortOrder;
    }

    public void setContent(String json, String by, Instant at) {
        this.contentJson = json;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public Long getId() { return id; }
    public Meeting getMeeting() { return meeting; }
    public String getSectionKey() { return sectionKey; }
    public String getName() { return name; }
    public TeamTag getTeamTag() { return teamTag; }
    public SectionKind getKind() { return kind; }
    public String getLayout() { return layout; }
    public OverflowRule getOverflowRule() { return overflowRule; }
    public boolean isRequired() { return required; }
    public String getColumnsSpec() { return columnsSpec; }
    public String getChartKpiKey() { return chartKpiKey; }
    public String getContentJson() { return contentJson; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public boolean isOnSlides() { return onSlides; }

    void setOnSlides(boolean onSlides) { this.onSlides = onSlides; }
    /** The Template version this Section's definition comes from: its own if it was switched on later, else the Meeting's. */
    public int templateVersionNo(int meetingVersion) { return templateVersionNo == null ? meetingVersion : templateVersionNo; }
    public boolean isSwitchedOn() { return switchedOn; }
    public boolean isHidden() { return hidden; }

    /** Available KPIs an editor switched on for this Meeting. */
    public java.util.List<String> getEnabledKpis() {
        return enabledKpis == null || enabledKpis.isBlank() ? java.util.List.of() : java.util.List.of(enabledKpis.split("\\|"));
    }

    void pinTo(int templateVersion) { this.templateVersionNo = templateVersion; }
    void markSwitchedOn() { this.switchedOn = true; }
    void setHidden(boolean hidden) { this.hidden = hidden; }
    void setEnabledKpis(java.util.List<String> keys) { this.enabledKpis = keys.isEmpty() ? null : String.join("|", keys); }
    void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    int getSortOrder() { return sortOrder; }
}
