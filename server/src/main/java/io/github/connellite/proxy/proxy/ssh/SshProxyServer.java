package io.github.connellite.proxy.proxy.ssh;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.Readable;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.keyboard.DefaultKeyboardInteractiveAuthenticator;
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator;
import org.apache.sshd.server.forward.AgentForwardingFilter;
import org.apache.sshd.server.forward.ForwardingFilter;
import org.apache.sshd.server.forward.TcpForwardingFilter;
import org.apache.sshd.server.forward.X11ForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionImpl;
import org.apache.sshd.server.session.SessionFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSH listener for authenticated TCP tunnels / port forwards (Apache Mina SSHD).
 * Interactive shell and exec are rejected with a GitHub-style notice.
 * Auth and connection/traffic limits reuse {@link io.github.connellite.proxy.model.ProxyUser}
 * via {@link ProxyAuthService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class SshProxyServer implements AutoCloseable {

    static final AttributeRepository.AttributeKey<AuthenticatedSession> AUTH_KEY =
            new AttributeRepository.AttributeKey<>();
    static final AttributeRepository.AttributeKey<ProxyMetrics.TrackedSession> TRACKED_KEY =
            new AttributeRepository.AttributeKey<>();

    private static final long TRAFFIC_FLUSH_BYTES = 64 * 1024L;

    private final ProxyAuthService authService;
    private final ProxyMetrics metrics;
    private final ProxyProperties properties;

    private SshServer server;

    public synchronized void start(String bindHost, int port) throws IOException {
        stop();
        Path hostKey = Path.of(properties.getDataDir()).toAbsolutePath().normalize().resolve("ssh-hostkey.ser");
        Files.createDirectories(hostKey.getParent());

        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setHost(bindHost);
        sshd.setPort(port);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey));
        sshd.setPasswordAuthenticator(this::authenticatePassword);
        sshd.setPublickeyAuthenticator(RejectAllPublickeyAuthenticator.INSTANCE);
        sshd.setKeyboardInteractiveAuthenticator(DefaultKeyboardInteractiveAuthenticator.INSTANCE);
        sshd.setShellFactory(channel -> new SshAuthMessageCommand());
        sshd.setCommandFactory((channel, command) -> new SshAuthMessageCommand());
        sshd.setSubsystemFactories(Collections.emptyList());
        sshd.setForwardingFilter(ForwardingFilter.asForwardingFilter(
                AgentForwardingFilter.of(false),
                X11ForwardingFilter.of(false),
                new QuotaAwareTcpForwardingFilter()));
        sshd.setSessionFactory(new SessionFactory(sshd) {
            @Override
            protected ServerSessionImpl doCreateSession(IoSession ioSession) throws Exception {
                return new TrafficAwareServerSession(getServer(), ioSession);
            }
        });
        sshd.addSessionListener(new ProxySshSessionListener());
        CoreModuleProperties.IDLE_TIMEOUT.set(sshd, Duration.ofSeconds(Math.max(1, properties.getIdleTimeoutSeconds())));
        CoreModuleProperties.AUTH_TIMEOUT.set(sshd, Duration.ofSeconds(Math.max(30, properties.getConnectTimeoutMs() / 1000)));

        sshd.start();
        this.server = sshd;
        log.info("SSH tunnel proxy listening on {}:{}", bindHost, port);
    }

    public synchronized boolean isRunning() {
        return server != null && server.isStarted();
    }

    @Override
    public synchronized void close() {
        stop();
    }

    public synchronized void stop() {
        SshServer current = server;
        server = null;
        if (current == null) {
            return;
        }
        try {
            current.stop(true);
        } catch (IOException ex) {
            log.warn("Error while stopping SSH proxy: {}", ex.toString());
        }
    }

    private boolean authenticatePassword(String username, String password, ServerSession session) {
        Optional<AuthenticatedSession> authenticated = authService.authenticate(username, password);
        if (authenticated.isEmpty()) {
            return false;
        }
        session.setAttribute(AUTH_KEY, authenticated.get());
        return true;
    }

    private boolean allowForward(Session session) {
        AuthenticatedSession auth = session.getAttribute(AUTH_KEY);
        return metrics.allowMoreTraffic(auth);
    }

    private final class QuotaAwareTcpForwardingFilter implements TcpForwardingFilter {
        @Override
        public boolean canListen(SshdSocketAddress address, Session session) {
            return allowForward(session);
        }

        @Override
        public boolean canConnect(Type type, SshdSocketAddress address, Session session) {
            return allowForward(session);
        }
    }

    private final class ProxySshSessionListener implements SessionListener {
        @Override
        public void sessionEvent(Session session, Event event) {
            if (event != Event.Authenticated || !(session instanceof ServerSession serverSession)) {
                return;
            }
            AuthenticatedSession auth = serverSession.getAttribute(AUTH_KEY);
            Optional<ProxyMetrics.TrackedSession> tracked = metrics.trackSession(auth);
            if (tracked.isEmpty()) {
                try {
                    serverSession.disconnect(SshConstants.SSH2_DISCONNECT_TOO_MANY_CONNECTIONS,
                            "Connection limit reached");
                } catch (IOException ex) {
                    log.debug("Failed to disconnect over-limit SSH session: {}", ex.toString());
                }
                return;
            }
            serverSession.setAttribute(TRACKED_KEY, tracked.get());
        }

        @Override
        public void sessionClosed(Session session) {
            if (session instanceof TrafficAwareServerSession trafficSession) {
                trafficSession.flushTraffic(true);
            }
            ProxyMetrics.TrackedSession tracked = session.removeAttribute(TRACKED_KEY);
            if (tracked != null) {
                tracked.close();
            }
        }
    }

    private final class TrafficAwareServerSession extends ServerSessionImpl {
        private final AtomicLong pendingUp = new AtomicLong();
        private final AtomicLong pendingDown = new AtomicLong();

        private TrafficAwareServerSession(ServerFactoryManager server, IoSession ioSession)
                throws Exception {
            super(server, ioSession);
        }

        @Override
        public void messageReceived(Readable buffer) throws Exception {
            if (isAuthenticated()) {
                account(pendingUp, buffer.available());
            }
            super.messageReceived(buffer);
        }

        @Override
        public IoWriteFuture writePacket(Buffer buffer) throws IOException {
            if (isAuthenticated()) {
                account(pendingDown, Math.max(0, buffer.wpos() - buffer.rpos()));
            }
            return super.writePacket(buffer);
        }

        private void account(AtomicLong counter, long bytes) throws IOException {
            if (bytes <= 0) {
                return;
            }
            long pending = counter.addAndGet(bytes);
            if (pending >= TRAFFIC_FLUSH_BYTES) {
                flushTraffic(false);
            }
            AuthenticatedSession auth = getAttribute(AUTH_KEY);
            if (!metrics.allowMoreTraffic(auth)) {
                disconnect(SshConstants.SSH2_DISCONNECT_CONNECTION_LOST, "Traffic limit exceeded");
            }
        }

        void flushTraffic(boolean force) {
            long up = pendingUp.getAndSet(0);
            long down = pendingDown.getAndSet(0);
            if (!force && up == 0 && down == 0) {
                return;
            }
            AuthenticatedSession auth = getAttribute(AUTH_KEY);
            if (auth != null && (up > 0 || down > 0)) {
                metrics.recordTraffic(auth.userId(), up, down);
            }
        }
    }
}
