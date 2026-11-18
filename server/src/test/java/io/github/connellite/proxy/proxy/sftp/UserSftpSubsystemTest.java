package io.github.connellite.proxy.proxy.sftp;

import io.github.connellite.proxy.proxy.ssh.sftp.SftpGrant;
import io.github.connellite.proxy.proxy.ssh.sftp.SftpNoticeSubsystem;
import io.github.connellite.proxy.proxy.ssh.sftp.SftpRootFileSystemFactory;
import io.github.connellite.proxy.proxy.ssh.sftp.UserSftpSubsystemFactory;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UserSftpSubsystemTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @TempDir
    static Path tempDir;

    private static Path root;
    private static Path outside;
    private static SshServer sshd;
    private static SshClient client;

    @BeforeAll
    static void startServer() throws IOException {
        root = Files.createDirectories(tempDir.resolve("root"));
        outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Files.writeString(root.resolve("existing.txt"), "hello");

        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator((user, password, session) -> "secret".equals(password));
        sshd.setSubsystemFactories(List.of(new UserSftpSubsystemFactory(session -> switch (session.getUsername()) {
            case "dave" -> Optional.of(new SftpGrant(root, false));
            case "rita" -> Optional.of(new SftpGrant(root, true));
            default -> Optional.empty();
        })));
        sshd.setFileSystemFactory(new SftpRootFileSystemFactory());
        sshd.start();

        client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (client != null) {
            client.stop();
        }
        if (sshd != null) {
            sshd.stop(true);
        }
    }

    @Test
    void readsAndWritesInsideRoot() throws IOException {
        try (ClientSession session = login("dave");
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            assertThat(sftp.canonicalPath(".")).isEqualTo("/");
            assertThat(names(sftp, "/")).contains("existing.txt");

            try (InputStream in = sftp.read("/existing.txt")) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
            }

            sftp.mkdir("/sub");
            try (OutputStream out = sftp.write("/sub/new.txt")) {
                out.write("uploaded".getBytes(StandardCharsets.UTF_8));
            }
            assertThat(Files.readString(root.resolve("sub").resolve("new.txt"))).isEqualTo("uploaded");
            assertThat(names(sftp, "/sub")).contains("new.txt");

            sftp.rename("/sub/new.txt", "/sub/renamed.txt");
            assertThat(Files.exists(root.resolve("sub").resolve("renamed.txt"))).isTrue();

            sftp.remove("/sub/renamed.txt");
            sftp.rmdir("/sub");
            assertThat(Files.exists(root.resolve("sub"))).isFalse();
        }
    }

    @Test
    void cannotLeaveRoot() throws IOException {
        try (ClientSession session = login("dave");
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            assertThat(sftp.canonicalPath("/../..")).isEqualTo("/");
            assertThat(names(sftp, "/..")).contains("existing.txt").doesNotContain("outside", "root");
            assertThatThrownBy(() -> sftp.stat("/../outside/secret.txt")).isInstanceOf(SftpException.class);
            assertThatThrownBy(() -> {
                try (OutputStream out = sftp.write("/../outside/evil.txt")) {
                    out.write(1);
                }
            }).isInstanceOf(IOException.class);
            assertThat(Files.exists(outside.resolve("evil.txt"))).isFalse();
        }
    }

    @Test
    void cannotFollowLinksOnDiskOutOfRoot() throws IOException {
        Path dirLink = root.resolve("escape");
        Path fileLink = root.resolve("leak.txt");
        try {
            Files.createSymbolicLink(dirLink, outside);
            Files.createSymbolicLink(fileLink, outside.resolve("secret.txt"));
        } catch (IOException | UnsupportedOperationException ex) {
            Files.deleteIfExists(dirLink);
            assumeTrue(false, "Symbolic links are not available: " + ex);
        }
        try (ClientSession session = login("dave");
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            // The folder with the links can still be listed.
            assertThat(names(sftp, "/")).contains("existing.txt");

            assertThatThrownBy(() -> {
                try (InputStream in = sftp.read("/escape/secret.txt")) {
                    in.readAllBytes();
                }
            }).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> names(sftp, "/escape")).hasMessageContaining("PERMISSION_DENIED");
            assertThatThrownBy(() -> {
                try (InputStream in = sftp.read("/leak.txt")) {
                    in.readAllBytes();
                }
            }).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> {
                try (OutputStream out = sftp.write("/escape/evil.txt")) {
                    out.write(1);
                }
            }).isInstanceOf(IOException.class);
            assertThat(Files.exists(outside.resolve("evil.txt"))).isFalse();
            assertThat(Files.readString(outside.resolve("secret.txt"))).isEqualTo("secret");
        } finally {
            Files.deleteIfExists(dirLink);
            Files.deleteIfExists(fileLink);
        }
    }

    @Test
    void clientCannotCreateLinkOutOfRoot() throws IOException {
        try (ClientSession session = login("dave");
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            assertThatThrownBy(() -> sftp.symLink("/out", "../outside")).isInstanceOf(IOException.class);
            assertThat(Files.exists(root.resolve("out"), java.nio.file.LinkOption.NOFOLLOW_LINKS)).isFalse();
        }
    }

    @Test
    void readOnlyUserCanReadButNotChange() throws IOException {
        Files.createDirectories(root.resolve("keep"));
        try (ClientSession session = login("rita");
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            assertThat(names(sftp, "/")).contains("existing.txt", "keep");
            try (InputStream in = sftp.read("/existing.txt")) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
            }

            assertDenied(() -> {
                try (OutputStream out = sftp.write("/new.txt")) {
                    out.write(1);
                }
            });
            assertDenied(() -> {
                try (OutputStream out = sftp.write("/existing.txt")) {
                    out.write(1);
                }
            });
            assertDenied(() -> sftp.mkdir("/made"));
            assertDenied(() -> sftp.rename("/existing.txt", "/moved.txt"));
            assertDenied(() -> sftp.remove("/existing.txt"));
            assertDenied(() -> sftp.rmdir("/keep"));
            assertDenied(() -> sftp.symLink("/link", "existing.txt"));
            SftpClient.Attributes attrs = new SftpClient.Attributes();
            attrs.setModifyTime(java.nio.file.attribute.FileTime.fromMillis(0));
            assertDenied(() -> sftp.setStat("/existing.txt", attrs));
        } finally {
            Files.deleteIfExists(root.resolve("keep"));
        }
        assertThat(Files.readString(root.resolve("existing.txt"))).isEqualTo("hello");
        assertThat(Files.exists(root.resolve("new.txt"))).isFalse();
        assertThat(Files.exists(root.resolve("made"))).isFalse();
        assertThat(Files.exists(root.resolve("moved.txt"))).isFalse();
        assertThat(Files.getLastModifiedTime(root.resolve("existing.txt")).toMillis()).isNotZero();
    }

    @Test
    void userWithoutFolderGetsNotice() throws IOException {
        try (ClientSession session = login("erin");
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            assertThatThrownBy(() -> sftp.canonicalPath("."))
                    .isInstanceOf(SftpException.class)
                    .hasMessageContaining(SftpNoticeSubsystem.messageFor("erin"));
        }
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(SftpException.class)
                .satisfies(ex -> assertThat(((SftpException) ex).getStatus())
                        .isEqualTo(org.apache.sshd.sftp.common.SftpConstants.SSH_FX_PERMISSION_DENIED));
    }

    private static List<String> names(SftpClient sftp, String dir) throws IOException {
        List<String> result = new ArrayList<>();
        for (SftpClient.DirEntry entry : sftp.readDir(dir)) {
            result.add(entry.getFilename());
        }
        return result;
    }

    private static ClientSession login(String user) throws IOException {
        ClientSession session = client.connect(user, "127.0.0.1", sshd.getPort()).verify(TIMEOUT).getSession();
        session.addPasswordIdentity("secret");
        session.auth().verify(TIMEOUT);
        return session;
    }
}
