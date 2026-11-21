package io.github.connellite.proxy.relay;

import java.util.Locale;

/** Normalizes and matches DNS names and the single leading-label mask. */
public final class RelayNames {

    private RelayNames() {
    }

    /** Query name: trailing dots removed, lower case. Empty or illegal input yields {@code null}. */
    public static String normalizeQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String name = stripDots(raw.trim().toLowerCase(Locale.ROOT));
        if (name.isEmpty() || name.length() > 253 || !labelsOk(name, true)) {
            return null;
        }
        return name;
    }

    /** Stored name. Rejects anything except a plain name or {@code *.example.com}. */
    public static String normalizePattern(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        String name = stripDots(raw.trim().toLowerCase(Locale.ROOT));
        if (name.isEmpty() || name.length() > 253 || !labelsOk(name, false)) {
            throw new IllegalArgumentException("Name must be a hostname or a mask like *.example.com");
        }
        return name;
    }

    public static boolean matches(String pattern, String query) {
        String[] patternLabels = pattern.split("\\.", -1);
        String[] queryLabels = query.split("\\.", -1);
        if ("*".equals(patternLabels[0])) {
            int suffix = patternLabels.length - 1;
            if (queryLabels.length <= suffix) {
                return false;
            }
            int offset = queryLabels.length - suffix;
            for (int i = 0; i < suffix; i++) {
                if (!queryLabels[offset + i].equals(patternLabels[i + 1])) {
                    return false;
                }
            }
            return true;
        }
        if (patternLabels.length != queryLabels.length) {
            return false;
        }
        for (int i = 0; i < patternLabels.length; i++) {
            if (!patternLabels[i].equals(queryLabels[i])) {
                return false;
            }
        }
        return true;
    }

    public static boolean wildcard(String pattern) {
        return pattern.startsWith("*.");
    }

    private static String stripDots(String name) {
        int end = name.length();
        while (end > 0 && name.charAt(end - 1) == '.') {
            end--;
        }
        return name.substring(0, end);
    }

    private static boolean labelsOk(String name, boolean query) {
        String[] labels = name.split("\\.", -1);
        if (labels.length == 0) {
            return false;
        }
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (label.isEmpty() || label.length() > 63) {
                return false;
            }
            if ("*".equals(label)) {
                if (query || i != 0 || labels.length < 2) {
                    return false;
                }
                continue;
            }
            if (label.indexOf('*') >= 0) {
                return false;
            }
            if (!hostnameLabel(label)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hostnameLabel(String label) {
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return label.charAt(0) != '-' && label.charAt(label.length() - 1) != '-';
    }
}
