package io.github.connellite.proxy.service;

/**
 * Instantaneous throughput for a proxy user (bytes per second).
 */
public record UserThroughput(long upBytesPerSec, long downBytesPerSec) {

    public static final UserThroughput ZERO = new UserThroughput(0, 0);

    public UserThroughput(long upBytesPerSec, long downBytesPerSec) {
        this.upBytesPerSec = Math.max(0, upBytesPerSec);
        this.downBytesPerSec = Math.max(0, downBytesPerSec);
    }

    public long totalBytesPerSec() {
        return upBytesPerSec + downBytesPerSec;
    }

    public boolean isIdle() {
        return upBytesPerSec == 0 && downBytesPerSec == 0;
    }
}
