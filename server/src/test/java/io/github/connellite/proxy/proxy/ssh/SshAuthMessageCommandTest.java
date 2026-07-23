package io.github.connellite.proxy.proxy.ssh;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SshAuthMessageCommandTest {

    @Test
    void buildsGithubStyleNotice() {
        assertThat(SshAuthMessageCommand.messageFor("alice"))
                .isEqualTo("Hi alice! You've successfully authenticated, but this proxy does not provide shell access.");
    }

    @Test
    void fallsBackWhenUsernameBlank() {
        assertThat(SshAuthMessageCommand.messageFor("  ")).startsWith("Hi user!");
    }
}
