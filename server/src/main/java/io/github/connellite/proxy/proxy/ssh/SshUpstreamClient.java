package io.github.connellite.proxy.proxy.ssh;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.github.connellite.proxy.proxy.OutboundConnector;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
#if SPRING_BOOT_3
import jakarta.annotation.PreDestroy;
#else
import javax.annotation.PreDestroy;
#endif
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbound tunnels through an upstream SSH server (local port forward → Netty).
 * <p>
 * Uses SSHD {@code startLocalPortForwarding} (same model as {@code ssh -L}). A known
 * DefaultForwarder race can log WARN/NPE while the tunnel still works; that logger is
 * quieted in {@code application.yml}.
 */
@Slf4j
@Component
public class SshUpstreamClient {

    private final ProxyProperties properties;
    private final ExecutorService executor;
    private final ConcurrentHashMap<Long, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> sessionLocks = new ConcurrentHashMap<>();
    private final Object clientLock = new Object();
    private volatile SshClient client;

    public SshUpstreamClient(ProxyProperties properties) {
        this.properties = properties;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ssh-upstream");
            t.setDaemon(true);
            return t;
        });
    }

    @SuppressWarnings("resource")
    public void openTunnel(Channel inbound,
                           UpstreamSnapshot upstream,
                           String targetHost,
                           int targetPort,
                           OutboundConnector.TunnelCallback callback) {
        executor.execute(() -> {
            SshdSocketAddress bound = null;
            ClientSession session = null;
            try {
                session = sessionFor(upstream);
                SshdSocketAddress local = new SshdSocketAddress("127.0.0.1", 0);
                SshdSocketAddress remote = new SshdSocketAddress(targetHost, targetPort);
                bound = session.startLocalPortForwarding(local, remote);
                SshdSocketAddress listening = bound;
                ClientSession sshSession = session;
                inbound.eventLoop().execute(() -> connectLoopback(inbound, sshSession, listening, callback));
            } catch (Exception ex) {
                stopForward(session, bound, new AtomicBoolean());
                inbound.eventLoop().execute(() -> callback.onFailure(wrap(ex)));
            }
        });
    }

    public void invalidate(long upstreamId) {
        SessionEntry removed = sessions.remove(upstreamId);
        closeQuietly(removed);
    }

    public void invalidateAll() {
        for (Long id : sessions.keySet()) {
            invalidate(id);
        }
    }

    @PreDestroy
    public void shutdown() {
        invalidateAll();
        executor.shutdownNow();
        SshClient current = client;
        client = null;
        if (current != null) {
            try {
                current.stop();
            } catch (Exception ex) {
                log.debug("SSH upstream client stop: {}", ex.toString());
            }
        }
    }

    private SshClient client() {
        SshClient current = client;
        if (current != null && current.isStarted()) {
            return current;
        }
        synchronized (clientLock) {
            current = client;
            if (current != null && current.isStarted()) {
                return current;
            }
            SshClient created = SshClient.setUpDefaultClient();
            created.setIoServiceFactoryFactory(ShutdownSafeNio2Executor.serviceFactoryFactory());
            created.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            created.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
            // Host/user/password come from Upstream settings — ignore ~/.ssh/config.
            created.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
            Duration idle = Duration.ofSeconds(Math.max(1, properties.getIdleTimeoutSeconds()));
            CoreModuleProperties.IDLE_TIMEOUT.set(created, idle);
            created.start();
            client = created;
            return created;
        }
    }

    private void connectLoopback(Channel inbound,
                                 ClientSession session,
                                 SshdSocketAddress bound,
                                 OutboundConnector.TunnelCallback callback) {
        Bootstrap bootstrap = new Bootstrap()
                .group(inbound.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        AtomicBoolean stopped = new AtomicBoolean();
                        ch.closeFuture().addListener(future -> stopForward(session, bound, stopped));
                    }
                });
        bootstrap.connect(bound.getHostName(), bound.getPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                callback.onSuccess(future.channel());
            } else {
                stopForward(session, bound, new AtomicBoolean());
                callback.onFailure(future.cause() != null
                        ? future.cause()
                        : new IllegalStateException("Unable to connect to SSH local forward"));
            }
        });
    }

    private ClientSession sessionFor(UpstreamSnapshot upstream) throws IOException {
        Object lock = sessionLocks.computeIfAbsent(upstream.id(), id -> new Object());
        synchronized (lock) {
            SessionEntry existing = sessions.get(upstream.id());
            if (existing != null && existing.matches(upstream) && existing.isUsable()) {
                return existing.session;
            }
            SessionEntry created = connectSession(upstream);
            SessionEntry previous = sessions.put(upstream.id(), created);
            if (previous != null && previous != created) {
                closeQuietly(previous);
            }
            return created.session;
        }
    }

    private SessionEntry connectSession(UpstreamSnapshot upstream) throws IOException {
        if (StringUtils.isBlank(upstream.username())) {
            throw new IllegalStateException("Upstream SSH requires a username");
        }
        Duration timeout = Duration.ofMillis(Math.max(1_000, properties.getConnectTimeoutMs()));
        ClientSession session = client().connect(upstream.username().trim(), upstream.host(), upstream.port())
                .verify(timeout)
                .getSession();
        session.addPasswordIdentity(StringUtils.defaultString(upstream.password()));
        try {
            session.auth().verify(timeout);
        } catch (IOException ex) {
            closeQuietly(session);
            throw new IllegalStateException("Upstream SSH authentication failed", ex);
        }
        if (!session.isAuthenticated()) {
            closeQuietly(session);
            throw new IllegalStateException("Upstream SSH authentication failed");
        }
        log.info("SSH upstream session established to {}:{} as {}", upstream.host(), upstream.port(), upstream.username());
        return new SessionEntry(upstream, session);
    }

    private static void stopForward(ClientSession session, SshdSocketAddress bound, AtomicBoolean stopped) {
        if (session == null || bound == null || !stopped.compareAndSet(false, true)) {
            return;
        }
        try {
            if (session.isOpen()) {
                session.stopLocalPortForwarding(bound);
            }
        } catch (Exception ex) {
            // ignore — session may already be closing
        }
    }

    private static void closeQuietly(SessionEntry entry) {
        if (entry == null) {
            return;
        }
        log.info("SSH upstream session closed to {}:{} as {}", entry.host, entry.port, entry.username);
        closeQuietly(entry.session);
    }

    private static void closeQuietly(ClientSession session) {
        if (session == null) {
            return;
        }
        try {
            session.close(true).await(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static Throwable wrap(Exception ex) {
        if (ex instanceof IllegalStateException) {
            return ex;
        }
        return new IllegalStateException("Upstream SSH tunnel failed: " + ex.getMessage(), ex);
    }

    private static final class SessionEntry {
        private final long id;
        private final String host;
        private final int port;
        private final String username;
        private final String password;
        private final ClientSession session;

        private SessionEntry(UpstreamSnapshot upstream, ClientSession session) {
            this.id = upstream.id();
            this.host = upstream.host();
            this.port = upstream.port();
            this.username = upstream.username();
            this.password = upstream.password();
            this.session = session;
        }

        private boolean matches(UpstreamSnapshot upstream) {
            return id == upstream.id()
                    && port == upstream.port()
                    && Objects.equals(host, upstream.host())
                    && Objects.equals(username, upstream.username())
                    && Objects.equals(password, upstream.password());
        }

        private boolean isUsable() {
            return session != null && session.isOpen() && session.isAuthenticated();
        }
    }
}
