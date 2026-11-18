package io.github.connellite.proxy.proxy.sftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@code sftp} subsystem: users with SFTP access get a real SFTP server rooted at their folder
 * (see {@link SftpRootFileSystemFactory}), read-only when the grant says so
 * (see {@link ReadOnlyAwareSftpFileSystemAccessor}); everyone else gets {@link SftpNoticeSubsystem}.
 * Access is resolved on every channel open, so admin changes apply to new connections.
 */
@Slf4j
public final class UserSftpSubsystemFactory implements SubsystemFactory {

    /** Grant resolved for the session's latest SFTP channel; read by the file system factory and accessor. */
    static final AttributeRepository.AttributeKey<SftpGrant> SFTP_GRANT_KEY = new AttributeRepository.AttributeKey<>();

    private final Function<ServerSession, Optional<SftpGrant>> grantResolver;
    private final SftpSubsystemFactory delegate = new SftpSubsystemFactory.Builder()
            .withFileSystemAccessor(ReadOnlyAwareSftpFileSystemAccessor.INSTANCE)
            .build();

    public UserSftpSubsystemFactory(Function<ServerSession, Optional<SftpGrant>> grantResolver) {
        this.grantResolver = Objects.requireNonNull(grantResolver, "grantResolver");
    }

    @Override
    public String getName() {
        return SftpNoticeSubsystem.NAME;
    }

    @Override
    public Command createSubsystem(ChannelSession channel) throws IOException {
        ServerSession session = channel.getServerSession();
        Optional<SftpGrant> grant = resolve(session);
        if (grant.isEmpty()) {
            return new SftpNoticeSubsystem();
        }
        session.setAttribute(SFTP_GRANT_KEY, grant.get());
        return delegate.createSubsystem(channel);
    }

    private Optional<SftpGrant> resolve(ServerSession session) {
        if (session == null) {
            return Optional.empty();
        }
        try {
            return grantResolver.apply(session);
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve SFTP access for {}: {}", session.getUsername(), ex.toString());
            return Optional.empty();
        }
    }
}
