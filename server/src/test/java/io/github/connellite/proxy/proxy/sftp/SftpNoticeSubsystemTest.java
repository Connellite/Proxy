package io.github.connellite.proxy.proxy.sftp;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Raw SFTP protocol check of the notice WinSCP and OpenSSH show to users without SFTP access. */
class SftpNoticeSubsystemTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static SshServer sshd;
    private static SshClient client;

    @BeforeAll
    static void startServer() throws IOException {
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator((user, password, session) -> "secret".equals(password));
        sshd.setSubsystemFactories(List.of(SftpNoticeSubsystem.factory()));
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
    void sftpSubsystemAnswersHandshakeThenDeniesWithNotice() throws IOException {
        String notice = SftpNoticeSubsystem.messageFor("carol");
        try (ClientSession session = login("carol");
             ClientChannel channel = session.createSubsystemChannel("sftp")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setOut(out);
            channel.setErr(err);
            channel.open().verify(TIMEOUT);

            ByteBuffer init = ByteBuffer.allocate(9).putInt(5).put((byte) 1).putInt(3);
            byte[] dot = ".".getBytes(StandardCharsets.US_ASCII);
            ByteBuffer realpath = ByteBuffer.allocate(4 + 1 + 4 + 4 + dot.length)
                    .putInt(1 + 4 + 4 + dot.length).put((byte) 16).putInt(42).putInt(dot.length).put(dot);
            OutputStream requests = channel.getInvertedIn();
            requests.write(init.array());
            requests.write(realpath.array());
            requests.flush();
            requests.close();
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TIMEOUT);

            ByteBuffer replies = ByteBuffer.wrap(out.toByteArray());
            assertThat(replies.getInt()).isEqualTo(5);
            assertThat(replies.get()).isEqualTo((byte) 2);
            assertThat(replies.getInt()).isEqualTo(3);

            int length = replies.getInt();
            assertThat(replies.remaining()).isEqualTo(length);
            assertThat(replies.get()).isEqualTo((byte) 101);
            assertThat(replies.getInt()).isEqualTo(42);
            assertThat(replies.getInt()).isEqualTo(3);
            byte[] text = new byte[replies.getInt()];
            replies.get(text);
            assertThat(new String(text, StandardCharsets.UTF_8)).isEqualTo(notice);

            assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo(notice + "\n");
            assertThat(channel.getExitStatus()).isEqualTo(1);
        }
    }

    private static ClientSession login(String user) throws IOException {
        ClientSession session = client.connect(user, "127.0.0.1", sshd.getPort()).verify(TIMEOUT).getSession();
        session.addPasswordIdentity("secret");
        session.auth().verify(TIMEOUT);
        return session;
    }
}
