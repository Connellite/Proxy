package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ClientHelloSplitHandlerTest {

    private static final String HOST = "example.com";

    @Test
    void fixedPositionCutsRecordAndKeepsHandshakeBytes() {
        byte[] record = clientHelloRecord(HOST);
        byte[] out = writeThrough(new ClientHelloSplitHandler(ClientHelloSplitMode.FIXED, 1, true), record);

        assertThat(out).hasSize(record.length + TlsRecordSplitter.RECORD_HEADER);
        assertThat(recordLengthAt(out, 0)).isEqualTo(1);
        assertThat(joinFragments(out)).isEqualTo(fragment(record));
        assertThat(out[0]).isEqualTo((byte) 22);
        assertThat(out[TlsRecordSplitter.RECORD_HEADER + 1]).isEqualTo((byte) 22);
    }

    @Test
    void beforeSniPutsWholeHostInSecondRecord() {
        byte[] record = clientHelloRecord(HOST);
        byte[] out = writeThrough(new ClientHelloSplitHandler(ClientHelloSplitMode.BEFORE_SNI, 1, true), record);

        int firstLength = recordLengthAt(out, 0);
        String first = new String(out, TlsRecordSplitter.RECORD_HEADER, firstLength, StandardCharsets.ISO_8859_1);
        int secondAt = TlsRecordSplitter.RECORD_HEADER + firstLength;
        String second = new String(out, secondAt + TlsRecordSplitter.RECORD_HEADER, recordLengthAt(out, secondAt),
                StandardCharsets.ISO_8859_1);

        assertThat(first).doesNotContain(HOST);
        assertThat(second).startsWith(HOST);
        assertThat(joinFragments(out)).isEqualTo(fragment(record));
    }

    @Test
    void sniOffsetsForEveryMode() {
        ByteBuf fragment = Unpooled.wrappedBuffer(fragment(clientHelloRecord(HOST)));
        int[] sni = TlsRecordSplitter.findSni(fragment);
        assertThat(sni).isNotNull();
        int start = sni[0];
        assertThat(sni[1]).isEqualTo(HOST.length());
        Random random = new Random(1);

        assertThat(TlsRecordSplitter.splitOffset(fragment, ClientHelloSplitMode.BEFORE_SNI, 1, random)).isEqualTo(start);
        assertThat(TlsRecordSplitter.splitOffset(fragment, ClientHelloSplitMode.AFTER_SNI, 1, random))
                .isEqualTo(start + HOST.length());
        assertThat(TlsRecordSplitter.splitOffset(fragment, ClientHelloSplitMode.MIDDLE_SNI, 1, random))
                .isEqualTo(start + HOST.length() / 2);
        for (int i = 0; i < 50; i++) {
            assertThat(TlsRecordSplitter.splitOffset(fragment, ClientHelloSplitMode.RANDOM_SNI, 1, random))
                    .isGreaterThan(start).isLessThan(start + HOST.length());
        }
        fragment.release();
    }

    @Test
    void recordWithoutSniStaysWhole() {
        byte[] record = clientHelloRecord(null);
        byte[] out = writeThrough(new ClientHelloSplitHandler(ClientHelloSplitMode.BEFORE_SNI, 1, true), record);

        assertThat(out).isEqualTo(record);
    }

    @Test
    void positionPastRecordStaysWhole() {
        byte[] record = clientHelloRecord(HOST);
        byte[] out = writeThrough(new ClientHelloSplitHandler(ClientHelloSplitMode.FIXED, 1500, true), record);

        assertThat(out).isEqualTo(record);
    }

    @Test
    void plainWritesBeforeArmDoNotUseTheCut() {
        EmbeddedChannel channel = new EmbeddedChannel(new ClientHelloSplitHandler(ClientHelloSplitMode.FIXED, 1, false));
        byte[] connect = "CONNECT example.com:443 HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] socks = {0x05, 0x01, 0x00};

        assertThat(writeAndRead(channel, connect)).isEqualTo(connect);
        assertThat(writeAndRead(channel, socks)).isEqualTo(socks);
        ClientHelloSplitHandler.arm(channel.pipeline());
        byte[] record = clientHelloRecord(HOST);
        byte[] out = writeAndRead(channel, record);

        assertThat(out).hasSize(record.length + TlsRecordSplitter.RECORD_HEADER);
        channel.finishAndReleaseAll();
    }

    @Test
    void firstWriteAfterArmThatIsNotTlsEndsTheAttempt() {
        EmbeddedChannel channel = new EmbeddedChannel(new ClientHelloSplitHandler(ClientHelloSplitMode.FIXED, 1, false));
        ClientHelloSplitHandler.arm(channel.pipeline());
        byte[] get = "GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] record = clientHelloRecord(HOST);

        assertThat(writeAndRead(channel, get)).isEqualTo(get);
        assertThat(writeAndRead(channel, record)).isEqualTo(record);
        channel.finishAndReleaseAll();
    }

    @Test
    void recordSpreadOverWritesIsCollectedFirst() {
        EmbeddedChannel channel = new EmbeddedChannel(new ClientHelloSplitHandler(ClientHelloSplitMode.BEFORE_SNI, 1, true));
        byte[] record = clientHelloRecord(HOST);
        int half = 20;

        channel.writeOutbound(Unpooled.wrappedBuffer(record, 0, half));
        assertThat((Object) channel.readOutbound()).isNull();
        byte[] out = writeAndRead(channel, Arrays.copyOfRange(record, half, record.length));

        assertThat(joinFragments(out)).isEqualTo(fragment(record));
        assertThat(out).hasSize(record.length + TlsRecordSplitter.RECORD_HEADER);
        channel.finishAndReleaseAll();
    }

    @Test
    void bytesAfterTheRecordFollowUnchanged() {
        byte[] record = clientHelloRecord(HOST);
        byte[] tail = {20, 3, 3, 0, 1, 1};
        byte[] in = new byte[record.length + tail.length];
        System.arraycopy(record, 0, in, 0, record.length);
        System.arraycopy(tail, 0, in, record.length, tail.length);

        byte[] out = writeThrough(new ClientHelloSplitHandler(ClientHelloSplitMode.FIXED, 1, true), in);

        assertThat(out).hasSize(in.length + TlsRecordSplitter.RECORD_HEADER);
        byte[] outTail = Arrays.copyOfRange(out, out.length - tail.length, out.length);
        assertThat(outTail).isEqualTo(tail);
    }

    private static byte[] writeThrough(ClientHelloSplitHandler handler, byte[] data) {
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        byte[] out = writeAndRead(channel, data);
        channel.finishAndReleaseAll();
        return out;
    }

    private static byte[] writeAndRead(EmbeddedChannel channel, byte[] data) {
        channel.writeOutbound(Unpooled.wrappedBuffer(data));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuf buf;
        while ((buf = channel.readOutbound()) != null) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            out.writeBytes(bytes);
        }
        return out.toByteArray();
    }

    private static int recordLengthAt(byte[] data, int at) {
        return ((data[at + 3] & 0xff) << 8) | (data[at + 4] & 0xff);
    }

    private static byte[] joinFragments(byte[] records) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int at = 0;
        while (at < records.length) {
            int length = recordLengthAt(records, at);
            out.write(records, at + TlsRecordSplitter.RECORD_HEADER, length);
            at += TlsRecordSplitter.RECORD_HEADER + length;
        }
        return out.toByteArray();
    }

    private static byte[] fragment(byte[] record) {
        return Arrays.copyOfRange(record, TlsRecordSplitter.RECORD_HEADER, record.length);
    }

    /** Minimal TLS 1.2 record carrying a ClientHello, with an SNI extension when {@code host} is set. */
    static byte[] clientHelloRecord(String host) {
        ByteArrayOutputStream extensions = new ByteArrayOutputStream();
        // supported_groups before SNI, so the name is not at a fixed offset
        writeShort(extensions, 10);
        writeShort(extensions, 4);
        writeShort(extensions, 2);
        writeShort(extensions, 29);
        if (host != null) {
            byte[] name = host.getBytes(StandardCharsets.US_ASCII);
            writeShort(extensions, 0);
            writeShort(extensions, name.length + 5);
            writeShort(extensions, name.length + 3);
            extensions.write(0);
            writeShort(extensions, name.length);
            extensions.writeBytes(name);
        }
        // extended_master_secret after SNI, as in real ClientHellos
        writeShort(extensions, 23);
        writeShort(extensions, 0);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeShort(body, 0x0303);
        body.writeBytes(new byte[32]);
        body.write(0);
        writeShort(body, 2);
        writeShort(body, 0x1301);
        body.write(1);
        body.write(0);
        writeShort(body, extensions.size());
        body.writeBytes(extensions.toByteArray());

        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        handshake.write(1);
        handshake.write(0);
        writeShort(handshake, body.size());
        handshake.writeBytes(body.toByteArray());

        ByteArrayOutputStream record = new ByteArrayOutputStream();
        record.write(22);
        writeShort(record, 0x0301);
        writeShort(record, handshake.size());
        record.writeBytes(handshake.toByteArray());
        return record.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value >> 8);
        out.write(value);
    }
}
