package io.github.connellite.proxy.dns.relay;

import com.google.common.net.InternetDomainName;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;

import java.util.Locale;

/**
 * Normalizes and matches DNS names and the single leading-label mask.
 */
@UtilityClass
public final class RelayNames {

    /**
     * Query name: trailing dots removed, lower case. Empty or illegal input yields {@code null}.
     */
    public static String normalizeQuery(String raw) {
        String name = prepare(raw);
        if (name == null || !InternetDomainName.isValid(name)) {
            return null;
        }
        return InternetDomainName.from(name).toString();
    }

    /**
     * Stored name. Rejects anything except a plain name or {@code *.example.com}.
     */
    public static String normalizePattern(String raw) {
        String name = prepare(raw);
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (name.startsWith("*.")) {
            String suffix = name.substring(2);
            if (!InternetDomainName.isValid(suffix)) {
                throw new IllegalArgumentException("Name must be a hostname or a mask like *.example.com");
            }
            return "*." + InternetDomainName.from(suffix);
        }
        if (!InternetDomainName.isValid(name)) {
            throw new IllegalArgumentException("Name must be a hostname or a mask like *.example.com");
        }
        return InternetDomainName.from(name).toString();
    }

    /**
     * {@code *.example.com} matches any name under that suffix, including several labels,
     * and does not match the suffix itself. An exact name matches only itself.
     */
    public static boolean matches(String pattern, String query) {
        try {
            Name patternName = Name.fromString(pattern.endsWith(".") ? pattern : pattern + ".");
            Name queryName = Name.fromString(query.endsWith(".") ? query : query + ".");
            if (!patternName.isWild()) {
                return patternName.equals(queryName);
            }
            Name suffix = new Name(patternName, 1);
            return queryName.labels() > suffix.labels() && queryName.subdomain(suffix);
        } catch (TextParseException ex) {
            return false;
        }
    }

    public static boolean wildcard(String pattern) {
        try {
            return Name.fromString(pattern + ".").isWild();
        } catch (TextParseException ex) {
            return false;
        }
    }

    private static String prepare(String raw) {
        if (raw == null) {
            return null;
        }
        String name = StringUtils.stripEnd(raw.trim().toLowerCase(Locale.ROOT), ".");
        if (name.isEmpty() || name.length() > 253) {
            return null;
        }
        return name;
    }
}
