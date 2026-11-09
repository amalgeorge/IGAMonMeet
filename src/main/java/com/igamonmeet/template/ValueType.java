package com.igamonmeet.template;

/** How a KPI's value is entered, stored and shown (docs/spec-v2.md §2.2). */
public enum ValueType {
    /** Any number, shown with the KPI's unit. */
    NUMBER,
    /** A number shown with %. */
    PERCENT,
    /** A whole number. */
    COUNT,
    /** Stored as minutes; entered and shown as "2h 30m". */
    DURATION,
    /** A number shown with a currency code, SGD by default. */
    CURRENCY,
    /** A label picked from an Admin-defined list; each label maps to a KPI status. No trend line. */
    TEXT_STATUS;

    public boolean numeric() {
        return this != TEXT_STATUS;
    }
}
