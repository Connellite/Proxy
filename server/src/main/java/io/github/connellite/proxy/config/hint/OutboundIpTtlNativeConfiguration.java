package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3

import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.io.FileDescriptor;
import java.lang.reflect.Method;

/**
 * GraalVM reflection/JNI metadata for {@link io.github.connellite.proxy.proxy.OutboundIpTtl}.
 * Without these hints, {@code Class.getDeclaredMethod("javaChannel")} fails at runtime in native
 * images with {@link NoSuchMethodException} even though the method exists on the JVM.
 */
@Configuration
@ImportRuntimeHints(OutboundIpTtlNativeConfiguration.Hints.class)
public class OutboundIpTtlNativeConfiguration {

    static final class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            registerJavaChannel(hints, NioSocketChannel.class);
            registerJavaChannel(hints, NioDatagramChannel.class);
            registerJavaChannel(hints, AbstractNioChannel.class);

            registerGetFd(hints, classLoader, "sun.nio.ch.SocketChannelImpl");
            registerGetFd(hints, classLoader, "sun.nio.ch.DatagramChannelImpl");
            registerSetIntOption0(hints, classLoader);
        }

        private static void registerJavaChannel(RuntimeHints hints, Class<?> type) {
            Method method = findDeclaredMethod(type, "javaChannel");
            if (method != null) {
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            }
        }

        private static void registerGetFd(RuntimeHints hints, ClassLoader classLoader, String className) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                Method getFd = findDeclaredMethod(type, "getFD");
                if (getFd != null) {
                    hints.reflection().registerMethod(getFd, ExecutableMode.INVOKE);
                }
            } catch (ClassNotFoundException ignored) {
                // JDK / platform variant without this channel impl
            }
        }

        private static void registerSetIntOption0(RuntimeHints hints, ClassLoader classLoader) {
            try {
                Class<?> net = Class.forName("sun.nio.ch.Net", false, classLoader);
                Method setInt = net.getDeclaredMethod(
                        "setIntOption0",
                        FileDescriptor.class,
                        boolean.class,
                        int.class,
                        int.class,
                        int.class,
                        boolean.class);
                hints.reflection().registerMethod(setInt, ExecutableMode.INVOKE);
                // Native method — Graal needs an explicit JNI hint as well as reflection.
                hints.jni().registerMethod(setInt, ExecutableMode.INVOKE);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("sun.nio.ch.Net.setIntOption0 missing", ex);
            }
        }

        private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    return current.getDeclaredMethod(name, parameterTypes);
                } catch (NoSuchMethodException ignored) {
                    // keep walking
                }
            }
            return null;
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class OutboundIpTtlNativeConfiguration {
    private OutboundIpTtlNativeConfiguration() {
    }
}
#endif
