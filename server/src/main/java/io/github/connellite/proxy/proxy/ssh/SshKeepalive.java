package io.github.connellite.proxy.proxy.ssh;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.buffer.Buffer;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenSSH {@code ClientAlive}: a global request that wants a reply. The reply resets SSHD's idle
 * clock. A silent peer still hits {@code idle-timeout}.
 */
@Slf4j
@UtilityClass
public final class SshKeepalive {

    private static final String REQUEST = "keepalive@openssh.com";

    private static final AttributeRepository.AttributeKey<ScheduledFuture<?>> TASK =
            new AttributeRepository.AttributeKey<>();

    /**
     * Seconds between probes. {@code count} below 1 is treated as 1. The interval stays shorter
     * than {@code idleSeconds} when that idle is longer than one second.
     */
    public static int intervalSeconds(int idleSeconds, int count) {
        int idle = Math.max(1, idleSeconds);
        int probes = Math.max(1, count);
        int interval = idle / probes;
        if (interval < 1) {
            interval = 1;
        }
        if (interval >= idle) {
            interval = Math.max(1, idle / 2);
        }
        return interval;
    }

    public static void start(Session session, int intervalSeconds) {
        ScheduledExecutorService scheduler = session.getFactoryManager().getScheduledExecutorService();
        if (scheduler == null) {
            return;
        }
        int interval = Math.max(1, intervalSeconds);
        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> send(session), interval, interval, TimeUnit.SECONDS);
        ScheduledFuture<?> previous = session.setAttribute(TASK, task);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public static void stop(Session session) {
        ScheduledFuture<?> task = session.removeAttribute(TASK);
        if (task != null) {
            task.cancel(false);
        }
    }

    private static void send(Session session) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Buffer buffer = session.createBuffer(SshConstants.SSH_MSG_GLOBAL_REQUEST, REQUEST.length() + Byte.SIZE);
            buffer.putString(REQUEST);
            buffer.putBoolean(true);
            session.request(buffer, REQUEST, null);
        } catch (IOException ex) {
            log.debug("SSH keepalive failed for {}: {}", session, ex.toString());
        }
    }
}
