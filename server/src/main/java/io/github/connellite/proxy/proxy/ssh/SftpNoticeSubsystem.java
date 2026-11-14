package io.github.connellite.proxy.proxy.ssh;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.subsystem.SubsystemFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Clients such as WinSCP only log the subsystem's stderr and report a generic
 * "Cannot initialize SFTP protocol" when the channel closes; a status reply is shown to the user verbatim.
 * The notice is also written to stderr for the OpenSSH {@code sftp} client, which prints it directly.
 */
final class SftpNoticeSubsystem implements Command, Runnable {

    static final String NAME = "sftp";

    private static final int SSH_FXP_INIT = 1;
    private static final int SSH_FXP_VERSION = 2;
    private static final int SSH_FXP_STATUS = 101;
    private static final int SSH_FX_PERMISSION_DENIED = 3;
    private static final int SFTP_VERSION = 3;
    /** Requests from a refused client are tiny; anything larger is not worth reading. */
    private static final int MAX_PACKET = 256 * 1024;

    private ChannelSession channel;
    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;

    static SubsystemFactory factory() {
        return new SubsystemFactory() {
            @Override
            public String getName() {
                return NAME;
            }

            @Override
            public Command createSubsystem(ChannelSession channel) {
                return new SftpNoticeSubsystem();
            }
        };
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
        this.channel = channel;
        Thread thread = new Thread(this, "ssh-no-sftp");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void destroy(ChannelSession channel) {
        // reader thread ends when the channel input closes
    }

    @Override
    public void run() {
        String username = channel == null || channel.getServerSession() == null
                ? null : channel.getServerSession().getUsername();
        String message = SshAuthMessageCommand.messageFor(username, "SFTP");
        try {
            if (err != null) {
                err.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                err.flush();
            }
            serve(message);
        } catch (IOException ignored) {
            // client closed the channel or sent garbage
        }
        if (callback != null) {
            callback.onExit(1, message);
        }
    }

    private void serve(String message) throws IOException {
        DataInputStream input = new DataInputStream(in);
        while (true) {
            int length;
            try {
                length = input.readInt();
            } catch (EOFException eof) {
                return;
            }
            if (length < 1 || length > MAX_PACKET) {
                return;
            }
            byte[] packet = new byte[length];
            input.readFully(packet);
            ByteBuffer body = ByteBuffer.wrap(packet);
            int type = body.get() & 0xFF;
            if (type == SSH_FXP_INIT) {
                writeVersion();
                continue;
            }
            if (body.remaining() >= 4) {
                writeStatus(body.getInt(), message);
            }
            // One denial is enough: closing stops WinSCP from opening an empty session afterwards.
            return;
        }
    }

    private void writeVersion() throws IOException {
        ByteBuffer reply = ByteBuffer.allocate(4 + 1 + 4);
        reply.putInt(1 + 4).put((byte) SSH_FXP_VERSION).putInt(SFTP_VERSION);
        send(reply);
    }

    private void writeStatus(int requestId, String message) throws IOException {
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        byte[] lang = "en".getBytes(StandardCharsets.US_ASCII);
        int payload = 1 + 4 + 4 + 4 + text.length + 4 + lang.length;
        ByteBuffer reply = ByteBuffer.allocate(4 + payload);
        reply.putInt(payload)
                .put((byte) SSH_FXP_STATUS)
                .putInt(requestId)
                .putInt(SSH_FX_PERMISSION_DENIED)
                .putInt(text.length).put(text)
                .putInt(lang.length).put(lang);
        send(reply);
    }

    private void send(ByteBuffer reply) throws IOException {
        out.write(reply.array(), 0, reply.position());
        out.flush();
    }
}
