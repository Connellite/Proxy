package io.github.connellite.proxy.proxy.ssh;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class SshAuthMessageCommandTest {

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
        sshd.setShellFactory(channel -> new SshAuthMessageCommand());
        sshd.setCommandFactory((channel, command) -> new SshAuthMessageCommand());
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
    void buildsGithubStyleNotice() {
        assertThat(SshAuthMessageCommand.messageFor("alice"))
                .isEqualTo("Hi alice! You've successfully authenticated, but this proxy does not provide shell access.");
    }

    @Test
    void fallsBackWhenUsernameBlank() {
        assertThat(SshAuthMessageCommand.messageFor("  ")).startsWith("Hi user!");
    }

    @Test
    void interactiveShellWithPtyPrintsNoticeAndExits() throws IOException {
        try (ClientSession session = login("alice");
             ClientChannel channel = session.createShellChannel()) {
            Result result = run(channel);
            assertThat(result.err).isEqualTo(SshAuthMessageCommand.messageFor("alice") + "\r\n");
            assertThat(result.exit).isEqualTo(1);
        }
    }

    @Test
    void execPrintsNoticeAndExits() throws IOException {
        try (ClientSession session = login("bob");
             ClientChannel channel = session.createExecChannel("ls")) {
            Result result = run(channel);
            assertThat(result.err).isEqualTo(SshAuthMessageCommand.messageFor("bob") + "\n");
            assertThat(result.exit).isEqualTo(1);
        }
    }

    private static ClientSession login(String user) throws IOException {
        ClientSession session = client.connect(user, "127.0.0.1", sshd.getPort()).verify(TIMEOUT).getSession();
        session.addPasswordIdentity("secret");
        session.auth().verify(TIMEOUT);
        return session;
    }

    private static Result run(ClientChannel channel) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOut(out);
        channel.setErr(err);
        channel.open().verify(TIMEOUT);
        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TIMEOUT);
        return new Result(err.toString(StandardCharsets.UTF_8), channel.getExitStatus());
    }

    private record Result(String err, Integer exit) {
    }
}
