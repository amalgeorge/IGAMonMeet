package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.igamonmeet.template.Direction;
import com.igamonmeet.template.KpiStatus;
import com.igamonmeet.template.TemplateKpi;
import java.math.BigDecimal;

/** One KPI as shown in a Meeting: definition, this month's value, computed and final status, last month's value. */
public record KpiView(String key, String name, String unit, BigDecimal target, Direction direction, BigDecimal amberThreshold,
                      boolean required, BigDecimal value, KpiStatus computedStatus, KpiStatus override, String reason,
                      KpiStatus status, BigDecimal previousValue) {

    static KpiView of(TemplateKpi k, JsonNode values, JsonNode previousValues) {
        JsonNode v = values == null ? null : values.get(k.getKpiKey());
        BigDecimal value = number(v);
        KpiStatus computed = KpiStatus.of(k.getDirection(), k.getTarget(), k.getAmberThreshold(), value);
        KpiStatus override = v != null && v.hasNonNull("override") ? KpiStatus.valueOf(v.get("override").asText()) : null;
        String reason = v != null && v.hasNonNull("reason") ? v.get("reason").asText() : null;
        BigDecimal previous = number(previousValues == null ? null : previousValues.get(k.getKpiKey()));
        return new KpiView(k.getKpiKey(), k.getName(), k.getUnit(), k.getTarget(), k.getDirection(), k.getAmberThreshold(), k.isRequired(),
                value, computed, override, reason, override != null ? override : computed, previous);
    }

    private static BigDecimal number(JsonNode v) {
        return v != null && v.path("value").isNumber() ? v.get("value").decimalValue() : null;
    }
}
