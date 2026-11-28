package io.github.connellite.proxy.proxy.outbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Rewrites the head of a plain HTTP/1.x request the way GoodbyeDPI does with {@code -a -r -s -m}:
 * <ul>
 *     <li>an extra space between the method and the request target;</li>
 *     <li>{@code Host} becomes {@code hoSt};</li>
 *     <li>no space after the colon of that header;</li>
 *     <li>the Host value in mixed case.</li>
 * </ul>
 * Other headers and the body are left as they are. Servers may reject such a request.
 */
@UtilityClass
public final class HttpHeaderMangler {

    private static final Pattern REQUEST_LINE = Pattern.compile("^[A-Z]+ \\S+ HTTP/1\\.[01]$");
    private static final byte[] HEAD_END = {'\r', '\n', '\r', '\n'};
    /** Request head larger than this is not treated as HTTP. */
    private static final int MAX_HEAD = 64 * 1024;

    /**
     * @return a new buffer with the rewritten request head followed by the rest of {@code buf},
     * or {@code null} if {@code buf} does not start with a complete HTTP/1.x request head
     */
    public static ByteBuf mangle(ByteBuf buf, ByteBufAllocator alloc) {
        int start = buf.readerIndex();
        int headEnd = indexOf(buf, start, Math.min(buf.writerIndex(), start + MAX_HEAD));
        if (headEnd < 0) {
            return null;
        }
        String head = buf.toString(start, headEnd - start, StandardCharsets.ISO_8859_1);
        String[] lines = head.split("\r\n", -1);
        if (!REQUEST_LINE.matcher(lines[0]).matches()) {
            return null;
        }
        StringBuilder out = new StringBuilder(head.length() + 2);
        int space = lines[0].indexOf(' ');
        out.append(lines[0], 0, space).append("  ").append(lines[0], space + 1, lines[0].length());
        for (int i = 1; i < lines.length; i++) {
            out.append("\r\n").append(mangleHeader(lines[i]));
        }
        out.append("\r\n\r\n");
        byte[] bytes = out.toString().getBytes(StandardCharsets.ISO_8859_1);
        int restIndex = headEnd + HEAD_END.length;
        int rest = buf.writerIndex() - restIndex;
        ByteBuf result = alloc.buffer(bytes.length + rest);
        result.writeBytes(bytes);
        result.writeBytes(buf, restIndex, rest);
        return result;
    }

    private static String mangleHeader(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0 || !"host".equalsIgnoreCase(line.substring(0, colon).trim())) {
            return line;
        }
        String value = line.substring(colon + 1).trim();
        return "hoSt:" + mixCase(value);
    }

    static String mixCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(i % 2 == 0 ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return out.toString();
    }

    private static int indexOf(ByteBuf buf, int from, int to) {
        for (int i = from; i + HEAD_END.length <= to; i++) {
            if (buf.getByte(i) == '\r' && buf.getByte(i + 1) == '\n'
                    && buf.getByte(i + 2) == '\r' && buf.getByte(i + 3) == '\n') {
                return i;
            }
        }
        return -1;
    }
}
