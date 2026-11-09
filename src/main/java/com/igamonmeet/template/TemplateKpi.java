package com.igamonmeet.template;

import java.math.BigDecimal;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "template_kpi")
public class TemplateKpi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_section_id")
    private TemplateSection section;

    private String kpiKey;
    private String name;
    private String unit;
    private BigDecimal target;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    /** Between target and this value the KPI is "Below target"; beyond it, "Off target". */
    private BigDecimal amberThreshold;

    private boolean required;
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    private ValueType valueType = ValueType.NUMBER;

    private String currency;
    private String statusOptions;
    private String subtitle;
    private String description;
    private Integer decimals;

    @Enumerated(EnumType.STRING)
    private Visibility visibility = Visibility.SHOWN;

    protected TemplateKpi() {
    }

    TemplateKpi(TemplateSection section, String kpiKey, String name, String unit, BigDecimal target, Direction direction,
                BigDecimal amberThreshold, boolean required, int sortOrder) {
        this.section = section;
        this.kpiKey = kpiKey;
        this.name = name;
        this.unit = unit;
        this.target = target;
        this.direction = direction;
        this.amberThreshold = amberThreshold;
        this.required = required;
        this.sortOrder = sortOrder;
    }

    public String getKpiKey() { return kpiKey; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getTarget() { return target; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmberThreshold() { return amberThreshold; }
    public boolean isRequired() { return required; }
    public Visibility getVisibility() { return visibility; }

    void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public ValueType getValueType() { return valueType == null ? ValueType.NUMBER : valueType; }
    public String getCurrency() { return currency; }
    public java.util.List<KpiFormat.StatusOption> getStatusOptions() { return KpiFormat.options(statusOptions); }

    public String getSubtitle() { return subtitle; }
    public String getDescription() { return description; }
    public Integer getDecimals() { return decimals; }

    void setDisplay(String subtitle, String description, Integer decimals) {
        this.subtitle = subtitle == null || subtitle.isBlank() ? null : subtitle.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.decimals = decimals;
    }

    void setValueSpec(ValueType valueType, String currency, String statusOptionsJson) {
        this.valueType = valueType == null ? ValueType.NUMBER : valueType;
        this.currency = currency;
        this.statusOptions = statusOptionsJson;
    }
}
