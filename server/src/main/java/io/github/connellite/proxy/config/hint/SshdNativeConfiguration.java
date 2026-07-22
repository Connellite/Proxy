package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientConnectionServiceFactory;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.forward.PortForwardingEventListener;
import org.apache.sshd.common.io.IoServiceEventListener;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.random.JceRandom;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.security.SunJCESecurityProviderRegistrar;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.apache.sshd.common.util.security.eddsa.EdDSASecurityProviderRegistrar;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerConnectionServiceFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Apache Mina SSHD uses JDK proxies for event listeners and reflective SPI/registrars.
 * Spring AOT turns these {@link RuntimeHints} into native-image metadata (same pattern as
 * the other hint configs) — no separate {@code META-INF/native-image/*.json} needed.
 */
@Configuration
@ImportRuntimeHints(SshdNativeConfiguration.Hints.class)
public class SshdNativeConfiguration {

    static final class Hints implements RuntimeHintsRegistrar {

        private static final MemberCategory[] TYPE_CATEGORIES = {
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS
        };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.proxies().registerJdkProxy(SessionListener.class);
            hints.proxies().registerJdkProxy(ChannelListener.class);
            hints.proxies().registerJdkProxy(PortForwardingEventListener.class);
            hints.proxies().registerJdkProxy(IoServiceEventListener.class);

            for (Class<?> type : new Class<?>[] {
                    SshClient.class,
                    SshServer.class,
                    SimpleGeneratorHostKeyProvider.class,
                    Nio2ServiceFactoryFactory.class,
                    Nio2ServiceFactory.class,
                    JceRandom.class,
                    ClientConnectionServiceFactory.class,
                    ServerConnectionServiceFactory.class,
                    SunJCESecurityProviderRegistrar.class,
                    BouncyCastleSecurityProviderRegistrar.class,
                    EdDSASecurityProviderRegistrar.class
            }) {
                hints.reflection().registerType(type, TYPE_CATEGORIES);
            }

            hints.resources().registerPattern("org/apache/sshd/sshd-version.properties");
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class SshdNativeConfiguration {
    private SshdNativeConfiguration() {
    }
}
#endif
