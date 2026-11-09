package com.igamonmeet.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.igamonmeet.template.Direction;
import com.igamonmeet.template.KpiFormat;
import com.igamonmeet.template.KpiStatus;
import com.igamonmeet.template.TemplateKpi;
import com.igamonmeet.template.ValueType;
import java.math.BigDecimal;
import java.util.List;

/**
 * One KPI as shown in a Meeting: definition, this month's value, computed and final status, last month's value, and the
 * same values formatted for display by value type (docs/spec-v2.md §2.2).
 */
public record KpiView(String key, String name, String unit, BigDecimal target, Direction direction, BigDecimal amberThreshold,
                      boolean required, BigDecimal value, KpiStatus computedStatus, KpiStatus override, String reason,
                      KpiStatus status, BigDecimal previousValue,
                      ValueType valueType, String text, String display, String targetLine, String deltaLine, List<KpiFormat.StatusOption> options,
                      String subtitle, String description, String statusLabel) {

    /** For callers that only know the v1 fields (planner tests). */
    public KpiView(String key, String name, String unit, BigDecimal target, Direction direction, BigDecimal amberThreshold,
                   boolean required, BigDecimal value, KpiStatus computedStatus, KpiStatus override, String reason,
                   KpiStatus status, BigDecimal previousValue) {
        this(key, name, unit, target, direction, amberThreshold, required, value, computedStatus, override, reason, status, previousValue,
                ValueType.NUMBER, null, KpiFormat.format(ValueType.NUMBER, unit, null, value),
                targetLine(ValueType.NUMBER, unit, null, null, direction, target), deltaLine(ValueType.NUMBER, unit, null, null, value, previousValue), List.of(),
                null, null, status == null ? null : DEFAULT_LABELS.get(status.name()));
    }

    static final java.util.Map<String, String> DEFAULT_LABELS = java.util.Map.of("ON_TARGET", "On target", "BELOW_TARGET", "Below target", "OFF_TARGET", "Off target");

    static KpiView of(TemplateKpi k, JsonNode values, JsonNode previousValues) {
        return of(k, values, previousValues, DEFAULT_LABELS);
    }

    /** {@code labels}: the Admin's names for the three statuses (docs/spec-v2.md §2.4). */
    static KpiView of(TemplateKpi k, JsonNode values, JsonNode previousValues, java.util.Map<String, String> labels) {
        ValueType type = k.getValueType();
        JsonNode v = values == null ? null : values.get(k.getKpiKey());
        KpiStatus override = v != null && v.hasNonNull("override") ? KpiStatus.valueOf(v.get("override").asText()) : null;
        String reason = v != null && v.hasNonNull("reason") ? v.get("reason").asText() : null;
        JsonNode pv = previousValues == null ? null : previousValues.get(k.getKpiKey());
        if (type == ValueType.TEXT_STATUS) {
            String text = text(v);
            KpiStatus computed = statusOf(k.getStatusOptions(), text);
            return new KpiView(k.getKpiKey(), k.getName(), k.getUnit(), k.getTarget(), k.getDirection(), k.getAmberThreshold(), k.isRequired(),
                    null, computed, override, reason, override != null ? override : computed, null,
                    type, text, text == null ? "—" : text, null, text(pv) == null ? null : "Last month: " + text(pv),
                    k.getStatusOptions(), k.getSubtitle(), k.getDescription(), label(labels, override != null ? override : computed));
        }
        BigDecimal value = number(v);
        BigDecimal previous = number(pv);
        KpiStatus computed = KpiStatus.of(k.getDirection(), k.getTarget(), k.getAmberThreshold(), value);
        return new KpiView(k.getKpiKey(), k.getName(), k.getUnit(), k.getTarget(), k.getDirection(), k.getAmberThreshold(), k.isRequired(),
                value, computed, override, reason, override != null ? override : computed, previous,
                type, null, KpiFormat.format(type, k.getUnit(), k.getCurrency(), k.getDecimals(), value),
                targetLine(type, k.getUnit(), k.getCurrency(), k.getDecimals(), k.getDirection(), k.getTarget()),
                deltaLine(type, k.getUnit(), k.getCurrency(), k.getDecimals(), value, previous), List.of(),
                k.getSubtitle(), k.getDescription(), label(labels, override != null ? override : computed));
    }

    /** The status a Text status label maps to; null when the label is empty or unknown. */
    public static KpiStatus statusOf(List<KpiFormat.StatusOption> options, String label) {
        if (label == null) return null;
        return options.stream().filter(o -> o.label().equalsIgnoreCase(label.trim())).map(KpiFormat.StatusOption::status).findFirst().orElse(null);
    }

    private static String label(java.util.Map<String, String> labels, KpiStatus s) {
        return s == null ? null : labels.getOrDefault(s.name(), DEFAULT_LABELS.get(s.name()));
    }

    private static String targetLine(ValueType type, String unit, String currency, Integer decimals, Direction direction, BigDecimal target) {
        return "Target " + (direction == Direction.HIGHER ? "≥ " : "≤ ") + KpiFormat.format(type, unit, currency, decimals, target);
    }

    private static String deltaLine(ValueType type, String unit, String currency, Integer decimals, BigDecimal value, BigDecimal previous) {
        if (value == null || previous == null) return null;
        BigDecimal diff = value.subtract(previous);
        return (diff.signum() >= 0 ? "+" : "−") + KpiFormat.format(type, unit, currency, decimals, diff.abs()) + " vs last month";
    }

    private static BigDecimal number(JsonNode v) {
        return v != null && v.path("value").isNumber() ? v.get("value").decimalValue() : null;
    }

    private static String text(JsonNode v) {
        return v != null && v.path("value").isTextual() && !v.get("value").asText().isBlank() ? v.get("value").asText() : null;
    }
}
