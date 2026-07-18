package io.github.connellite.proxy.controller;

import org.springframework.stereotype.Component;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component("fmt")
public class FormatHelper {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public String bytes(long bytes) {
        long abs = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (abs < 1024) {
            return bytes + " B";
        }
        long value = abs;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && abs > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %cB", value / 1024.0, ci.current());
    }

    /** Throughput, e.g. {@code 1.2 MB/s}. */
    public String rate(long bytesPerSec) {
        if (bytesPerSec <= 0) {
            return "0 B/s";
        }
        return bytes(bytesPerSec) + "/s";
    }

    public String instant(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return DATE_TIME.format(instant);
    }
}
