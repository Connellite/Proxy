package io.github.connellite.proxy.client.util;

public final class Formatters {

    private Formatters() {
    }

    /** Decimal units like the old {@code FormatHelper#bytes}. */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            long abs = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : -bytes;
            return "-" + formatBytes(abs);
        }
        if (bytes < 1000) {
            return bytes + ".0 B";
        }
        double value = bytes;
        String[] units = {"kB", "MB", "GB", "TB", "PB"};
        int unit = -1;
        do {
            value /= 1000.0;
            unit++;
        } while (value >= 1000.0 && unit < units.length - 1);
        return round1(value) + " " + units[unit];
    }

    public static String formatRate(long bps) {
        if (bps <= 0) {
            return "0.0 B/s";
        }
        return formatBytes(bps) + "/s";
    }

    /** Values {@code < 0} render as the infinity symbol. */
    public static String formatLimit(long value) {
        return value < 0 ? "∞" : formatBytes(value);
    }

    /** Values {@code < 0} render as the infinity symbol. */
    public static String formatRateLimit(long bps) {
        return bps < 0 ? "∞" : formatRate(bps);
    }

    public static String dash(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }

    public static String never(String value) {
        return value == null || value.isEmpty() ? "never" : value;
    }

    private static String round1(double value) {
        long scaled = Math.round(value * 10.0);
        long whole = scaled / 10;
        long frac = Math.abs(scaled % 10);
        return whole + "." + frac;
    }
}
