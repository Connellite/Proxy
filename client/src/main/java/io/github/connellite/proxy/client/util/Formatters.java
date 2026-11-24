package io.github.connellite.proxy.client.util;

import io.github.connellite.proxy.client.messages.AppMessages;
import io.github.connellite.proxy.client.messages.CommonMessages;

public final class Formatters {

    private Formatters() {
    }

    /** Decimal byte units (kB, MB, …). */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            long abs = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : -bytes;
            return "-" + formatBytes(abs);
        }
        CommonMessages text = AppMessages.COMMON;
        if (bytes == 0) {
            return text.zeroBytes();
        }
        if (bytes < 1000) {
            return text.bytesWithUnit(bytes + ".0", text.unitB());
        }
        double value = bytes;
        String[] units = {text.unitKb(), text.unitMb(), text.unitGb(), text.unitTb(), text.unitPb()};
        int unit = -1;
        do {
            value /= 1000.0;
            unit++;
        } while (value >= 1000.0 && unit < units.length - 1);
        return AppMessages.COMMON.bytesWithUnit(round1(value), units[unit]);
    }

    public static String formatRate(long bps) {
        if (bps <= 0) {
            return AppMessages.COMMON.zeroRate();
        }
        return AppMessages.COMMON.rate(formatBytes(bps));
    }

    /** Values {@code < 0} render as the infinity symbol. */
    public static String formatLimit(long value) {
        return value < 0 ? AppMessages.COMMON.infinity() : formatBytes(value);
    }

    /** Values {@code < 0} render as the infinity symbol. */
    public static String formatRateLimit(long bps) {
        return bps < 0 ? AppMessages.COMMON.infinity() : formatRate(bps);
    }

    public static String dash(String value) {
        return value == null || value.isEmpty() ? AppMessages.COMMON.dash() : value;
    }

    public static String never(String value) {
        return value == null || value.isEmpty() ? AppMessages.COMMON.never() : value;
    }

    private static String round1(double value) {
        long scaled = Math.round(value * 10.0);
        long whole = scaled / 10;
        long frac = Math.abs(scaled % 10);
        return whole + "." + frac;
    }
}
