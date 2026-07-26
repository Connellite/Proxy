package io.github.connellite.proxy.proxy;

import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sets IP TTL / IPv6 hop limit on outbound sockets opened by this proxy
 * (ZeroOmega → proxy → direct/upstream).
 * <p>
 * {@code 0} means leave the OS default. Requires JVM
 * {@code --add-opens java.base/sun.nio.ch=ALL-UNNAMED} for TCP (also declared in the Boot JAR manifest).
 * Native images need {@link io.github.connellite.proxy.config.hint.OutboundIpTtlNativeConfiguration}.
 */
@Slf4j
public final class OutboundIpTtl {

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    private static final boolean LINUX = OS.contains("linux");
    /**
     * {@code IPPROTO_IP}
     */
    private static final int IPPROTO_IP = 0;
    /**
     * {@code IPPROTO_IPV6}
     */
    private static final int IPPROTO_IPV6 = 41;
    /**
     * Linux {@code IP_TTL}=2; Windows/macOS {@code IP_TTL}=4
     */
    private static final int IP_TTL = LINUX ? 2 : 4;
    /**
     * Linux {@code IPV6_UNICAST_HOPS}=16; Windows/macOS=4
     */
    private static final int IPV6_UNICAST_HOPS = LINUX ? 16 : 4;

    private OutboundIpTtl() {
    }

    /**
     * @return {@code true} if {@code ttl} is in the valid config range (0–255).
     */
    public static boolean isValid(int ttl) {
        return ttl >= 0 && ttl <= 255;
    }

    /**
     * Apply outbound TTL when {@code ttl > 0}. No-op for {@code 0} (OS default).
     * Failures are logged once and do not abort the connection.
     */
    public static void apply(Channel channel, int ttl) {
        if (ttl <= 0 || channel == null) {
            return;
        }
        if (ttl > 255) {
            log.debug("Ignoring outbound TTL {}: must be 1–255", ttl);
            return;
        }
        try {
            if (channel instanceof DatagramChannel) {
                ((DatagramChannel) channel).config().setTimeToLive(ttl);
                if (channel instanceof NioDatagramChannel) {
                    try {
                        applyNioChannel(invokeJavaChannel(channel), ttl);
                    } catch (Throwable ignored) {
                        // IPv4 TTL already set via DatagramChannelConfig; native IPv6 is best-effort.
                    }
                }
                return;
            }
            if (channel instanceof NioSocketChannel) {
                applyNioChannel(invokeJavaChannel(channel), ttl);
                return;
            }
            warnOnce("Outbound TTL is not supported for channel type " + channel.getClass().getName());
        } catch (Throwable ex) {
            warnOnce("Unable to set outbound TTL " + ttl + ": " + ex);
        }
    }

    /**
     * Reach the JDK {@code SocketChannel}/{@code DatagramChannel} behind Netty NIO.
     * {@code NioSocketChannel#javaChannel()} is protected, so reflection is required.
     */
    private static Object invokeJavaChannel(Channel channel) throws Exception {
        Method method = channel.getClass().getDeclaredMethod("javaChannel");
        method.setAccessible(true);
        return method.invoke(channel);
    }

    /**
     * Obtain the native {@link FileDescriptor} from {@code sun.nio.ch.SocketChannelImpl}
     * (same approach as typical “extra socket options” tutorials).
     * <p>
     *
     * @see <a href="https://blog.termian.dev/posts/java-socket-native-options/">Java Socket native options</a>
     * — section “File Descriptor” / {@code SocketChannelImpl.fd}
     */
    private static void applyNioChannel(Object javaChannel, int ttl) throws Exception {
        Method getFd = javaChannel.getClass().getDeclaredMethod("getFD");
        getFd.setAccessible(true);
        FileDescriptor fd = (FileDescriptor) getFd.invoke(javaChannel);
        setIpTtl(fd, ttl);
    }

    /**
     * Set unicast {@code IP_TTL} / {@code IPV6_UNICAST_HOPS}. Not exposed as a public
     * {@link java.net.StandardSocketOptions} value (only {@code IP_MULTICAST_TTL} is);
     * tutorials usually call OS {@code setsockopt} via JNA after obtaining the fd — we call
     * the JDK’s own {@code sun.nio.ch.Net.setIntOption0} instead of shipping JNA.
     * <p>
     *
     * @see <a href="https://blog.termian.dev/posts/java-socket-native-options/">Java Socket native options</a>
     * — {@code setsockopt} / {@code setTtl}
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/StandardSocketOptions.html">StandardSocketOptions</a>
     * — no unicast {@code IP_TTL}
     */
    private static void setIpTtl(FileDescriptor fd, int ttl) throws Exception {
        Class<?> net = Class.forName("sun.nio.ch.Net");
        Method setInt = net.getDeclaredMethod(
                "setIntOption0",
                FileDescriptor.class,
                boolean.class,
                int.class,
                int.class,
                int.class,
                boolean.class);
        setInt.setAccessible(true);
        // mayNeedConversion=true matches JDK Net usage for TTL-like options.
        setInt.invoke(null, fd, false, IPPROTO_IP, IP_TTL, ttl, true);
        try {
            setInt.invoke(null, fd, true, IPPROTO_IPV6, IPV6_UNICAST_HOPS, ttl, true);
        } catch (Throwable ignored) {
            // Dual-stack / IPv4-only sockets may reject the IPv6 option.
        }
    }

    private static void warnOnce(String message) {
        if (WARNED.compareAndSet(false, true)) {
            log.warn("{} (need --add-opens java.base/sun.nio.ch=ALL-UNNAMED for TCP TTL)", message);
        } else {
            log.debug("{}", message);
        }
    }
}
