package com.igamonmeet.template;

import java.util.Set;

/** What a Section holds; decides its editor and which House template layouts may show it. */
public enum SectionKind {
    SUMMARY(Set.of("IAM_SUMMARY")),
    KPI(Set.of("IAM_KPI")),
    CHART(Set.of("IAM_CHART")),
    TABLE(Set.of("IAM_TABLE")),
    TWO_COLUMN(Set.of("IAM_TWO_COLUMN")),
    ACTION_ITEMS(Set.of("IAM_TABLE"));

    private final Set<String> layouts;

    SectionKind(Set<String> layouts) {
        this.layouts = layouts;
    }

    public boolean allows(String layout) {
        return layouts.contains(layout);
    }
}
