package com.igamonmeet.house;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The House template contract (ADR 0005): every layout and the placeholder names the app fills and reads. */
public final class HouseLayouts {

    public static final Map<String, List<String>> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("IAM_TITLE", List.of("title", "subtitle", "meta"));
        REQUIRED.put("IAM_SECTION", List.of("title", "body"));
        REQUIRED.put("IAM_SUMMARY", List.of("team", "title", "body"));
        REQUIRED.put("IAM_KPI", List.of("team", "title", "body"));
        REQUIRED.put("IAM_CHART", List.of("team", "title", "chart", "body"));
        REQUIRED.put("IAM_TABLE", List.of("team", "title"));
        REQUIRED.put("IAM_TWO_COLUMN", List.of("team", "title", "leftHead", "left", "rightHead", "right"));
        REQUIRED.put("IAM_CLOSING", List.of("title", "body"));
    }

    private HouseLayouts() {
    }
}
