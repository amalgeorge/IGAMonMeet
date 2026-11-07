package com.igamonmeet.template;

import java.math.BigDecimal;

/** CONTEXT.md "KPI status": computed from value, target, direction and amber threshold. */
public enum KpiStatus {
    ON_TARGET, BELOW_TARGET, OFF_TARGET;

    /** Null when there is no value yet. Values exactly on the target or the threshold count as the better status. */
    public static KpiStatus of(Direction direction, BigDecimal target, BigDecimal amber, BigDecimal value) {
        if (value == null) {
            return null;
        }
        int sign = direction == Direction.HIGHER ? 1 : -1;
        if (sign * value.compareTo(target) >= 0) {
            return ON_TARGET;
        }
        if (sign * value.compareTo(amber) >= 0) {
            return BELOW_TARGET;
        }
        return OFF_TARGET;
    }
}
