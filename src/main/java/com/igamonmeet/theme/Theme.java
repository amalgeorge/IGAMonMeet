package com.igamonmeet.theme;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/** A named Theme (CONTEXT.md "Theme"). Its look lives in versions; one Theme is the default for new Meetings. */
@Entity
@Table(name = "theme")
public class Theme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "is_default")
    private boolean defaultTheme;

    private String createdBy;
    private Instant createdAt;

    protected Theme() {
    }

    Theme(String name, String createdBy, Instant createdAt) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isDefaultTheme() { return defaultTheme; }

    void rename(String name) { this.name = name; }
    void setDefaultTheme(boolean d) { this.defaultTheme = d; }
}
