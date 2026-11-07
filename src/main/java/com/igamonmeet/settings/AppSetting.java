package com.igamonmeet.settings;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(name = "setting_value")
    private String value;

    private String updatedBy;
    private Instant updatedAt;

    protected AppSetting() {
    }

    public String getKey() { return key; }
    public String getValue() { return value; }

    void update(String value, String by, Instant at) {
        this.value = value;
        this.updatedBy = by;
        this.updatedAt = at;
    }
}
