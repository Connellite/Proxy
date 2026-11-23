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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenSSH {@code ClientAlive}: a global request that wants a reply. SSHD resets its idle clock on
 * every outgoing packet, so the probes themselves keep {@code idle-timeout} from firing. A peer
 * that leaves {@code maxMissed} probes in a row unanswered is closed here instead.
 */
@Slf4j
@UtilityClass
public final class SshKeepalive {

    private static final String REQUEST = "keepalive@openssh.com";

    private static final AttributeRepository.AttributeKey<ScheduledFuture<?>> TASK =
            new AttributeRepository.AttributeKey<>();

    private static final AttributeRepository.AttributeKey<AtomicInteger> MISSED =
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

    public static void start(Session session, int intervalSeconds, int maxMissed) {
        ScheduledExecutorService scheduler = session.getFactoryManager().getScheduledExecutorService();
        if (scheduler == null) {
            return;
        }
        int interval = Math.max(1, intervalSeconds);
        int limit = Math.max(1, maxMissed);
        session.setAttribute(MISSED, new AtomicInteger());
        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> send(session, limit), interval, interval, TimeUnit.SECONDS);
        ScheduledFuture<?> previous = session.setAttribute(TASK, task);
        if (previous != null) {
            previous.cancel(false);
        }
        // sessionClosed may have run before the task was stored
        if (!session.isOpen()) {
            stop(session);
        }
    }

    public static void stop(Session session) {
        session.removeAttribute(MISSED);
        ScheduledFuture<?> task = session.removeAttribute(TASK);
        if (task != null) {
            task.cancel(false);
        }
    }

    private static void send(Session session, int maxMissed) {
        AtomicInteger missed = session.getAttribute(MISSED);
        if (!session.isOpen() || missed == null) {
            stop(session);
            return;
        }
        if (missed.getAndIncrement() >= maxMissed) {
            log.info("SSH keepalive: {} probes unanswered, closing {}", maxMissed, session);
            stop(session);
            session.close(true);
            return;
        }
        try {
            Buffer buffer = session.createBuffer(SshConstants.SSH_MSG_GLOBAL_REQUEST, REQUEST.length() + Byte.SIZE);
            buffer.putString(REQUEST);
            buffer.putBoolean(true);
            session.request(buffer, REQUEST, (cmd, reply) -> missed.set(0));
        } catch (IOException ex) {
            log.debug("SSH keepalive failed for {}: {}", session, ex.toString());
        }
    }
}
