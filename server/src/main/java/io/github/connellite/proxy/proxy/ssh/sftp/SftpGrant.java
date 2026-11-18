package io.github.connellite.proxy.proxy.ssh.sftp;

import java.nio.file.Path;

/**
 * What an SFTP session may use: {@code root} becomes {@code /}; {@code readOnly} blocks every change.
 */
public record SftpGrant(Path root, boolean readOnly) {
}
