package io.github.connellite.proxy.config;

import org.springframework.stereotype.Component;
import wtf.metio.storageunits.model.StorageUnits;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component("fmt")
public class FormatHelper {

    private static final String BYTES_PATTERN = "0.0";

    private static final DateTimeFormatter DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DateTimeFormatter dateTime;

    public FormatHelper(ZoneId appZoneId) {
        this.dateTime = DATE_TIME_PATTERN.withZone(appZoneId);
    }

    public String bytes(long bytes) {
        if (bytes < 0) {
            long abs = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : -bytes;
            return "-" + StorageUnits.decimalValueOf(abs).asBestMatchingDecimalUnit().toString(BYTES_PATTERN);
        }
        return StorageUnits.decimalValueOf(bytes).asBestMatchingDecimalUnit().toString(BYTES_PATTERN);
    }

    /**
     * Throughput, e.g. {@code 1.2 MB/s}.
     */
    public String rate(long bytesPerSec) {
        if (bytesPerSec <= 0) {
            return "0.0 B/s";
        }
        return bytes(bytesPerSec) + "/s";
    }

    public String instant(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return dateTime.format(instant);
    }
}
