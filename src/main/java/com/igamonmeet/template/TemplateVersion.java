package com.igamonmeet.template;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "template_version")
public class TemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int versionNo;
    private String createdBy;
    private Instant createdAt;
    private String note;

    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder")
    private List<TemplateSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder")
    private List<TemplateGroup> groups = new ArrayList<>();

    protected TemplateVersion() {
    }

    TemplateVersion(int versionNo, String createdBy, Instant createdAt, String note) {
        this.versionNo = versionNo;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.note = note;
    }

    void add(TemplateSection s) {
        sections.add(s);
    }

    void add(TemplateGroup g) {
        groups.add(g);
    }

    public Long getId() { return id; }
    public int getVersionNo() { return versionNo; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getNote() { return note; }
    public List<TemplateSection> getSections() { return sections; }
    public List<TemplateGroup> getGroups() { return groups; }

    public java.util.Optional<TemplateGroup> group(String key) {
        return key == null ? java.util.Optional.empty() : groups.stream().filter(g -> g.getGroupKey().equals(key)).findFirst();
    }

    /** A Section's own Visibility, made stricter by its group's: a Retired group retires its Sections, an Available one makes them Available. */
    public Visibility effectiveVisibility(TemplateSection s) {
        Visibility own = s.getVisibility();
        Visibility grp = group(s.getGroupKey()).map(TemplateGroup::getVisibility).orElse(Visibility.SHOWN);
        return own.ordinal() >= grp.ordinal() ? own : grp;
    }
}
