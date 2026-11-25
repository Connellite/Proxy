package io.github.connellite.proxy.proxy.ssh;

import io.github.connellite.proxy.config.ProxyProperties;
import io.github.connellite.proxy.dto.AuthenticatedSession;
import io.github.connellite.proxy.proxy.ProxyListener;
import io.github.connellite.proxy.proxy.QuietCloseHandlerAdapter;
import io.github.connellite.proxy.proxy.ssh.sftp.SftpGrant;
import io.github.connellite.proxy.proxy.ssh.sftp.SftpRootFileSystemFactory;
import io.github.connellite.proxy.proxy.ssh.sftp.UserSftpSubsystemFactory;
import io.github.connellite.proxy.service.ProxyAllowCatalog;
import io.github.connellite.proxy.service.ProxyAuthService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.SftpAccessService;
import io.github.connellite.proxy.service.UserConnectionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.Readable;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.keyboard.DefaultKeyboardInteractiveAuthenticator;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSH listener for authenticated TCP tunnels / port forwards (Apache Mina SSHD).
 * An interactive shell is rejected with a GitHub-style notice. An exec command is served as
 * SFTP whatever the command text is, including a configured path such as {@code sudo /usr/lib/sftp-server}.
 * SFTP is served from the user's SFTP folder (chrooted as {@code /}, read-only or read/write) when the user has
 * {@link io.github.connellite.proxy.model.SftpAccess}, otherwise rejected the same way.
 * Auth and connection/traffic limits reuse {@link io.github.connellite.proxy.model.ProxyUser}
 * via {@link ProxyAuthService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class SshProxyServer implements ProxyListener, AutoCloseable {

    public static final AttributeRepository.AttributeKey<AuthenticatedSession> AUTH_KEY =
            new AttributeRepository.AttributeKey<>();
    public static final AttributeRepository.AttributeKey<ProxyMetrics.TrackedSession> TRACKED_KEY =
            new AttributeRepository.AttributeKey<>();
    private static final AttributeRepository.AttributeKey<UserConnectionLog.Tracker> CONNECTION_LOG =
            new AttributeRepository.AttributeKey<>();

    private static final long TRAFFIC_FLUSH_BYTES = 64 * 1024L;

    private final ProxyAuthService authService;
    private final ProxyMetrics metrics;
    private final ProxyProperties properties;
    private final SftpAccessService sftpAccessService;
    private final UserConnectionLog connectionLog;
    private final ProxyAllowCatalog allowCatalog;

    private SshServer server;

    public synchronized void start(String bindHost, int port) throws IOException {
        stop();
        Path dataDir = Path.of(properties.getDataDir()).toAbsolutePath().normalize();
        Files.createDirectories(dataDir);
        List<SshHostKeyType> hostKeys = properties.getSsh().getHostKeys();

        SshServer sshd = SshServer.setUpDefaultServer();
        // Windows NIO2 race on stop(): AcceptTask.failed after group executor shutdown (mina-sshd#409).
        sshd.setIoServiceFactoryFactory(ShutdownSafeNio2Executor.serviceFactoryFactory());
        sshd.setHost(bindHost);
        sshd.setPort(port);
        sshd.setKeyPairProvider(hostKeyProvider(dataDir, hostKeys));
        sshd.setPasswordAuthenticator(this::authenticatePassword);
        sshd.setPublickeyAuthenticator(this::authenticatePublicKey);
        sshd.setKeyboardInteractiveAuthenticator(DefaultKeyboardInteractiveAuthenticator.INSTANCE);
        sshd.setShellFactory(channel -> new SshAuthMessageCommand());
        UserSftpSubsystemFactory sftp = new UserSftpSubsystemFactory(this::sftpGrant);
        sshd.setCommandFactory((channel, command) -> sftp.createSubsystem(channel));
        sshd.setSubsystemFactories(List.of(sftp));
        sshd.setFileSystemFactory(new SftpRootFileSystemFactory());
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
        log.info("SSH tunnel proxy listening on {}:{} with host keys {}", bindHost, port,
                hostKeys.stream().map(SshHostKeyType::getSshName).distinct().toList());
    }

    /**
     * One generated key per type, each in its own file under {@code dataDir}. SSHD offers the client
     * only the host key algorithms these keys cover.
     */
    private static KeyPairProvider hostKeyProvider(Path dataDir, List<SshHostKeyType> types) {
        if (types == null || types.isEmpty()) {
            throw new IllegalStateException("proxy.ssh.host-keys must list at least one host key type");
        }
        List<KeyPairProvider> providers = new ArrayList<>();
        for (SshHostKeyType type : new LinkedHashSet<>(types)) {
            SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(dataDir.resolve(type.getFileName()));
            provider.setAlgorithm(type.getAlgorithm());
            provider.setKeySize(type.getKeySize());
            providers.add(provider);
        }
        return session -> KeyIdentityProvider.iterableOf(session, providers);
    }

    @Override
    public synchronized boolean isRunning() {
        return server != null && server.isStarted();
    }

    public String getProtocol() {
        return "SSH";
    }

    @Override
    public synchronized void close() {
        stop();
    }

    @Override
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
        return bindSession(authService.authenticateSshPassword(username, password), session);
    }

    private boolean authenticatePublicKey(String username, PublicKey key, ServerSession session) {
        return bindSession(authService.authenticatePublicKey(username, key), session);
    }

    private static boolean bindSession(Optional<AuthenticatedSession> authenticated, ServerSession session) {
        if (authenticated.isEmpty()) {
            return false;
        }
        session.setAttribute(AUTH_KEY, authenticated.get());
        return true;
    }

    private Optional<SftpGrant> sftpGrant(ServerSession session) {
        AuthenticatedSession auth = session.getAttribute(AUTH_KEY);
        return auth == null ? Optional.empty() : sftpAccessService.findGrant(auth.userId());
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
        public void sessionCreated(Session session) {
            InetSocketAddress remote = remote(session);
            InetAddress address = remote == null ? null : remote.getAddress();
            if (allowCatalog.allows(address)) {
                return;
            }
            session.close(true);
        }

        @Override
        public void sessionEvent(Session session, Event event) {
            if (event != Event.Authenticated || !(session instanceof ServerSession serverSession)) {
                return;
            }
            AuthenticatedSession auth = serverSession.getAttribute(AUTH_KEY);
            Optional<ProxyMetrics.TrackedSession> tracked = metrics.trackSession(auth);
            if (tracked.isEmpty()) {
                connectionLog.open(remote(serverSession), auth == null ? null : auth.userId(), getProtocol()).finish("Limit");
                try {
                    serverSession.disconnect(SshConstants.SSH2_DISCONNECT_TOO_MANY_CONNECTIONS,
                            "Connection limit reached");
                } catch (IOException ex) {
                    log.debug("Failed to disconnect over-limit SSH session: {}", ex.toString());
                }
                return;
            }
            serverSession.setAttribute(TRACKED_KEY, tracked.get());
            serverSession.setAttribute(CONNECTION_LOG, connectionLog.open(
                    remote(serverSession), auth == null ? null : auth.userId(), getProtocol()));
            if (properties.isSshKeepaliveEnabled()) {
                int interval = SshKeepalive.intervalSeconds(properties.getIdleTimeoutSeconds(), properties.getSshKeepaliveCount());
                SshKeepalive.start(serverSession, interval, properties.getSshKeepaliveCount());
            }
        }

        @Override
        public void sessionClosed(Session session) {
            SshKeepalive.stop(session);
            UserConnectionLog.Tracker tracker = session.removeAttribute(CONNECTION_LOG);
            if (tracker != null) {
                tracker.finish("Connected");
            }
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

        /**
         * SSHD logs every {@code exceptionCaught} on an open session at warn. A client that
         * drops the socket is not a fault: the session is closed either way.
         * Windows NIO.2 reports that drop as a plain {@link IOException}. The Win32 code is
         * not kept on the exception, and subclasses such as {@code SshException} stay warnings.
         */
        @Override
        public void exceptionCaught(Throwable t) {
            if (isClientDisconnect(t)) {
                debug("exceptionCaught({}) peer closed the connection: {}", this, t.toString(), t);
                close(true);
                return;
            }
            super.exceptionCaught(t);
        }

        private static boolean isClientDisconnect(Throwable cause) {
            if (QuietCloseHandlerAdapter.isBenignClose(cause)) {
                return true;
            }
            Throwable current = cause;
            int depth = 0;
            while (current != null && depth < 8) {
                if (current instanceof SshException) {
                    return false;
                }
                if (current.getClass() == IOException.class) {
                    return true;
                }
                Throwable next = current.getCause();
                if (next == current) {
                    break;
                }
                current = next;
                depth++;
            }
            return false;
        }

        @Override
        public void messageReceived(Readable buffer) throws Exception {
            if (isAuthenticated()) {
                account(pendingUp, buffer.available(), true);
            }
            super.messageReceived(buffer);
        }

        @Override
        public IoWriteFuture writePacket(Buffer buffer) throws IOException {
            if (isAuthenticated()) {
                account(pendingDown, Math.max(0, buffer.wpos() - buffer.rpos()), false);
            }
            return super.writePacket(buffer);
        }

        private void account(AtomicLong counter, long bytes, boolean upload) throws IOException {
            if (bytes <= 0) {
                return;
            }
            long pending = counter.addAndGet(bytes);
            UserConnectionLog.Tracker tracker = getAttribute(CONNECTION_LOG);
            if (tracker != null) {
                if (upload) {
                    tracker.addUp(bytes);
                } else {
                    tracker.addDown(bytes);
                }
            }
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

    private static InetSocketAddress remote(Session session) {
        if (session == null || session.getIoSession() == null) {
            return null;
        }
        SocketAddress address = session.getIoSession().getRemoteAddress();
        if (address instanceof InetSocketAddress inet) {
            return inet;
        }
        return null;
    }
}
