package io.github.connellite.proxy.proxy.ssh.sftp;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Chroot for SFTP: the user's folder becomes {@code /}. Paths that resolve outside the root are rejected,
 * including through {@code ..} and links on disk (see {@link ConfinedRootedFileSystemProvider}).
 * Same approach as {@link org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory}, but the root
 * comes from {@link UserSftpSubsystemFactory#SFTP_GRANT_KEY} instead of a static map.
 */
public final class SftpRootFileSystemFactory implements FileSystemFactory {

    @Override
    public Path getUserHomeDir(SessionContext session) {
        SftpGrant grant = session == null ? null : session.getAttribute(UserSftpSubsystemFactory.SFTP_GRANT_KEY);
        return grant == null ? null : grant.root();
    }

    @Override
    public FileSystem createFileSystem(SessionContext session) throws IOException {
        Path root = getUserHomeDir(session);
        if (root == null) {
            throw new IOException("SFTP is not enabled for this account");
        }
        return new ConfinedRootedFileSystemProvider().newFileSystem(root, Collections.emptyMap());
    }
}
