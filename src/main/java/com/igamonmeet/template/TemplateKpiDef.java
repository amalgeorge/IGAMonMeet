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

/** One KPI in a Template version's KPI library (docs/spec-v2.md §2.1). */
@Entity
@Immutable
@Table(name = "template_kpi_def")
public class TemplateKpiDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_version_id")
    private TemplateVersion version;

    private String kpiKey;
    private String name;
    private String unit;
    private BigDecimal target;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private BigDecimal amberThreshold;
    private int sortOrder;

    protected TemplateKpiDef() {
    }

    TemplateKpiDef(TemplateVersion version, String kpiKey, String name, String unit, BigDecimal target, Direction direction,
                   BigDecimal amberThreshold, int sortOrder) {
        this.version = version;
        this.kpiKey = kpiKey;
        this.name = name;
        this.unit = unit;
        this.target = target;
        this.direction = direction;
        this.amberThreshold = amberThreshold;
        this.sortOrder = sortOrder;
    }

    public String getKpiKey() { return kpiKey; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getTarget() { return target; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmberThreshold() { return amberThreshold; }
}
