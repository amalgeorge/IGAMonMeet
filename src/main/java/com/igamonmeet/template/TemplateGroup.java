package com.igamonmeet.template;

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

/** A Section group in one Template version (CONTEXT.md "Section group"). */
@Entity
@Immutable
@Table(name = "template_group")
public class TemplateGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_version_id")
    private TemplateVersion version;

    private String groupKey;
    private String name;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    private boolean divider;
    private int sortOrder;

    protected TemplateGroup() {
    }

    TemplateGroup(TemplateVersion version, String groupKey, String name, Visibility visibility, boolean divider, int sortOrder) {
        this.version = version;
        this.groupKey = groupKey;
        this.name = name;
        this.visibility = visibility;
        this.divider = divider;
        this.sortOrder = sortOrder;
    }

    public String getGroupKey() { return groupKey; }
    public String getName() { return name; }
    public Visibility getVisibility() { return visibility; }
    public boolean isDivider() { return divider; }
}
