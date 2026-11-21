package io.github.connellite.proxy.proxy.ssh.sftp;

import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Principal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every SFTP change (open for write, mkdir, link, rename, copy, delete, set attributes) goes through
 * {@link SftpFileSystemAccessor}; this one refuses them with {@code SSH_FX_PERMISSION_DENIED} when the
 * session's {@link SftpGrant} is read-only. Reads use the stock implementation.
 */
public final class ReadOnlyAwareSftpFileSystemAccessor implements SftpFileSystemAccessor {

    public static final ReadOnlyAwareSftpFileSystemAccessor INSTANCE = new ReadOnlyAwareSftpFileSystemAccessor();

    private static final Set<StandardOpenOption> WRITE_OPTIONS = EnumSet.of(
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
            StandardOpenOption.CREATE,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.DELETE_ON_CLOSE);

    private ReadOnlyAwareSftpFileSystemAccessor() {
    }

    @Override
    public SeekableByteChannel openFile(
            SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file,
            String handle, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        for (OpenOption option : options) {
            if (WRITE_OPTIONS.contains(option)) {
                denyIfReadOnly(subsystem, file);
                break;
            }
        }
        return SftpFileSystemAccessor.super.openFile(subsystem, fileHandle, file, handle, options, attrs);
    }

    @Override
    public void applyExtensionFileAttributes(
            SftpSubsystemProxy subsystem, Path file, Map<String, byte[]> extensions, LinkOption... options)
            throws IOException {
        if (extensions != null && !extensions.isEmpty()) {
            denyIfReadOnly(subsystem, file);
        }
        SftpFileSystemAccessor.super.applyExtensionFileAttributes(subsystem, file, extensions, options);
    }

    @Override
    public void setFileAttribute(
            SftpSubsystemProxy subsystem, Path file, String view, String attribute, Object value,
            LinkOption... options) throws IOException {
        denyIfReadOnly(subsystem, file);
        SftpFileSystemAccessor.super.setFileAttribute(subsystem, file, view, attribute, value, options);
    }

    @Override
    public void setFileOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options)
            throws IOException {
        denyIfReadOnly(subsystem, file);
        SftpFileSystemAccessor.super.setFileOwner(subsystem, file, value, options);
    }

    @Override
    public void setGroupOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options)
            throws IOException {
        denyIfReadOnly(subsystem, file);
        SftpFileSystemAccessor.super.setGroupOwner(subsystem, file, value, options);
    }

    @Override
    public void setFilePermissions(
            SftpSubsystemProxy subsystem, Path file, Set<PosixFilePermission> perms, LinkOption... options)
            throws IOException {
        denyIfReadOnly(subsystem, file);
        SftpFileSystemAccessor.super.setFilePermissions(subsystem, file, perms, options);
    }

    @Override
    public void setFileAccessControl(
            SftpSubsystemProxy subsystem, Path file, List<AclEntry> acl, LinkOption... options)
            throws IOException {
        denyIfReadOnly(subsystem, file);
        SftpFileSystemAccessor.super.setFileAccessControl(subsystem, file, acl, options);
    }

    @Override
    public void createDirectory(SftpSubsystemProxy subsystem, Path path) throws IOException {
        denyIfReadOnly(subsystem, path);
        SftpFileSystemAccessor.super.createDirectory(subsystem, path);
    }

    @Override
    public void createLink(SftpSubsystemProxy subsystem, Path link, Path existing, boolean symLink)
            throws IOException {
        denyIfReadOnly(subsystem, link);
        SftpFileSystemAccessor.super.createLink(subsystem, link, existing, symLink);
    }

    @Override
    public void renameFile(SftpSubsystemProxy subsystem, Path oldPath, Path newPath, Collection<CopyOption> opts)
            throws IOException {
        denyIfReadOnly(subsystem, oldPath);
        SftpFileSystemAccessor.super.renameFile(subsystem, oldPath, newPath, opts);
    }

    @Override
    public void copyFile(SftpSubsystemProxy subsystem, Path src, Path dst, Collection<CopyOption> opts)
            throws IOException {
        denyIfReadOnly(subsystem, dst);
        SftpFileSystemAccessor.super.copyFile(subsystem, src, dst, opts);
    }

    @Override
    public void removeFile(SftpSubsystemProxy subsystem, Path path, boolean isDirectory) throws IOException {
        denyIfReadOnly(subsystem, path);
        SftpFileSystemAccessor.super.removeFile(subsystem, path, isDirectory);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private static void denyIfReadOnly(SftpSubsystemProxy subsystem, Path path) throws AccessDeniedException {
        ServerSession session = subsystem == null ? null : subsystem.getServerSession();
        SftpGrant grant = session == null ? null : session.getAttribute(UserSftpSubsystemFactory.SFTP_GRANT_KEY);
        // No grant means the file system could not have been created; fail closed anyway.
        if (grant == null || grant.readOnly()) {
            throw new AccessDeniedException(String.valueOf(path), null, "Read-only SFTP access");
        }
    }
}
