package io.github.connellite.proxy.proxy.ssh;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.channel.ChannelSessionAware;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shell/exec handler that prints a GitHub-style notice and exits (no interactive shell).
 */
final class SshAuthMessageCommand implements Command, ChannelSessionAware, Runnable {

    private ChannelSession channelSession;
    @SuppressWarnings("unused")
    private InputStream in;
    @SuppressWarnings("unused")
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;

    static String messageFor(String username) {
        String user = username == null || username.isBlank() ? "user" : username.trim();
        return "Hi " + user + "! You've successfully authenticated, but this proxy does not provide shell access.";
    }

    @Override
    public void setChannelSession(ChannelSession session) {
        this.channelSession = session;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) {
        Thread thread = new Thread(this, "ssh-no-shell");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void destroy(ChannelSession channel) {
        // no-op
    }

    @Override
    public void run() {
        String username = null;
        if (channelSession != null) {
            ServerSession session = channelSession.getServerSession();
            if (session != null) {
                username = session.getUsername();
            }
        }
        String message = messageFor(username);
        try {
            if (err != null) {
                err.write(message.getBytes(StandardCharsets.UTF_8));
                err.write('\n');
                err.flush();
            }
        } catch (IOException ignored) {
            // client already gone
        }
        if (callback != null) {
            callback.onExit(1, message);
        }
    }
}
