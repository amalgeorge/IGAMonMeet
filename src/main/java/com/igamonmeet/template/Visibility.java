package com.igamonmeet.template;

/** How the Template offers a Section, Section group or KPI (CONTEXT.md "Visibility", docs/spec-v2.md §1.1). */
public enum Visibility {
    /** In every new Meeting. */
    SHOWN,
    /** Hidden until an editor switches it on for one Meeting. */
    AVAILABLE,
    /** Hidden from new Meetings; past Meetings and trend history are kept. */
    RETIRED
}
