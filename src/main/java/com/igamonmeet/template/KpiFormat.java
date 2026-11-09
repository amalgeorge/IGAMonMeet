package com.igamonmeet.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** Formats KPI values the same way for the Document view, slides, export and Dashboard. */
public final class KpiFormat {

    public record StatusOption(String label, KpiStatus status) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String DEFAULT_CURRENCY = "SGD";

    private KpiFormat() {
    }

    public static String format(ValueType type, String unit, String currency, BigDecimal v) {
        return format(type, unit, currency, null, v);
    }

    /** As above, rounded to {@code decimals} places when set (docs/spec-v2.md §2.3). */
    public static String format(ValueType type, String unit, String currency, Integer decimals, BigDecimal v) {
        if (v == null) {
            return "—";
        }
        if (decimals != null && type != ValueType.COUNT && type != ValueType.DURATION) {
            v = v.setScale(decimals, RoundingMode.HALF_UP);
        }
        return switch (type) {
            case PERCENT -> plain(v, decimals) + "%";
            case COUNT -> grouped(v.setScale(0, RoundingMode.HALF_UP), 0);
            case DURATION -> duration(v);
            case CURRENCY -> (currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency) + " " + grouped(v, decimals == null ? 2 : decimals);
            case TEXT_STATUS -> plain(v, decimals);
            case NUMBER -> plain(v, decimals) + ("%".equals(unit) ? "%" : unit == null || unit.isBlank() ? "" : " " + unit);
        };
    }

    /** Minutes as "2h 30m", "45m" or "3h". */
    public static String duration(BigDecimal minutes) {
        long m = minutes.setScale(0, RoundingMode.HALF_UP).longValue();
        long h = Math.abs(m) / 60;
        long r = Math.abs(m) % 60;
        String s = h == 0 ? r + "m" : r == 0 ? h + "h" : h + "h " + r + "m";
        return m < 0 ? "−" + s : s;
    }

    public static List<StatusOption> options(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<StatusOption>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String optionsJson(List<StatusOption> options) {
        try {
            return options == null || options.isEmpty() ? null : JSON.writeValueAsString(options);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String plain(BigDecimal v, Integer decimals) {
        return decimals != null ? v.toPlainString() : v.stripTrailingZeros().toPlainString();
    }

    private static String grouped(BigDecimal v, int decimals) {
        DecimalFormat f = new DecimalFormat(decimals == 0 ? "#,##0" : "#,##0." + "0".repeat(decimals), DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return f.format(v);
    }
}
