package io.github.connellite.proxy.proxy.ssh;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.channel.ChannelSessionAware;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.subsystem.SubsystemFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shell/exec/subsystem handler that prints a GitHub-style notice to stderr and exits with
 * status 1 (no interactive shell, no SFTP). Only reachable after successful authentication.
 */
final class SshAuthMessageCommand implements Command, ChannelSessionAware, Runnable {

    static final String SFTP_SUBSYSTEM = "sftp";

    private final String service;

    private ChannelSession channelSession;
    @SuppressWarnings("unused")
    private InputStream in;
    @SuppressWarnings("unused")
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private boolean pty;

    SshAuthMessageCommand() {
        this("shell");
    }

    SshAuthMessageCommand(String service) {
        this.service = service;
    }

    /** Answers {@code sftp} (and modern {@code scp}, which runs over SFTP) with the notice instead of a bare failure. */
    static SubsystemFactory sftpSubsystemFactory() {
        return new SubsystemFactory() {
            @Override
            public String getName() {
                return SFTP_SUBSYSTEM;
            }

            @Override
            public Command createSubsystem(ChannelSession channel) {
                return new SshAuthMessageCommand("SFTP");
            }
        };
    }

    static String messageFor(String username) {
        return messageFor(username, "shell");
    }

    static String messageFor(String username, String service) {
        String user = username == null || username.isBlank() ? "user" : username.trim();
        return "Hi " + user + "! You've successfully authenticated, but this proxy does not provide " + service + " access.";
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
        if (channel != null) {
            this.channelSession = channel;
        }
        this.pty = hasPty(env);
        Thread thread = new Thread(this, "ssh-no-shell");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean hasPty(Environment env) {
        if (env == null) {
            return false;
        }
        Map<?, ?> modes = env.getPtyModes();
        Map<String, String> vars = env.getEnv();
        return (modes != null && !modes.isEmpty())
                || (vars != null && vars.containsKey(Environment.ENV_TERM));
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
        String message = messageFor(username, service);
        try {
            if (err != null) {
                // With a PTY the client terminal is in raw mode: a bare LF would not return the carriage.
                err.write((message + (pty ? "\r\n" : "\n")).getBytes(StandardCharsets.UTF_8));
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
