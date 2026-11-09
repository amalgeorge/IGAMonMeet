package com.igamonmeet.template;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "template_section")
public class TemplateSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_version_id")
    private TemplateVersion version;

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

    /** Table column headings, separated by '|'. */
    @Column(name = "columns_spec")
    private String columnsSpec;

    private String chartKpiKey;
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    private Visibility visibility = Visibility.SHOWN;

    private boolean onSlides = true;

    private String groupKey;
    private String kpiLayout;
    private String tableStyle;

    @OneToMany(mappedBy = "section", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder")
    private List<TemplateKpi> kpis = new ArrayList<>();

    protected TemplateSection() {
    }

    TemplateSection(TemplateVersion version, String sectionKey, String name, TeamTag teamTag, SectionKind kind, String layout,
                    OverflowRule overflowRule, boolean required, String columnsSpec, String chartKpiKey, int sortOrder) {
        this.version = version;
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

    void add(TemplateKpi k) {
        kpis.add(k);
    }

    public String getSectionKey() { return sectionKey; }
    public String getName() { return name; }
    public TeamTag getTeamTag() { return teamTag; }
    public SectionKind getKind() { return kind; }
    public String getLayout() { return layout; }
    public OverflowRule getOverflowRule() { return overflowRule; }
    public boolean isRequired() { return required; }
    public String getColumnsSpec() { return columnsSpec; }
    public String getChartKpiKey() { return chartKpiKey; }
    public List<TemplateKpi> getKpis() { return kpis; }
    public Visibility getVisibility() { return visibility; }
    public boolean isOnSlides() { return onSlides; }
    public String getGroupKey() { return groupKey; }

    void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public String getKpiLayout() { return kpiLayout; }
    void setKpiLayout(String kpiLayout) { this.kpiLayout = kpiLayout; }
    public String getTableStyle() { return tableStyle; }
    void setTableStyle(String tableStyle) { this.tableStyle = tableStyle; }

    void setVisibility(Visibility visibility, boolean onSlides) {
        this.visibility = visibility;
        this.onSlides = onSlides;
    }

    /** KPIs that appear in new Meetings. */
    public List<TemplateKpi> getShownKpis() {
        return kpis.stream().filter(k -> k.getVisibility() == Visibility.SHOWN).collect(java.util.stream.Collectors.toList());
    }
}
