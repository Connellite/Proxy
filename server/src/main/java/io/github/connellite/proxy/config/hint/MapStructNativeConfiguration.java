package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import io.github.connellite.proxy.mapper.AppSettingsMapper;
import io.github.connellite.proxy.mapper.PasswordChangeFormMapper;
import io.github.connellite.proxy.mapper.ProxyUserFormMapper;
import io.github.connellite.proxy.mapper.TlsStatusMapper;
import io.github.connellite.proxy.mapper.UpstreamProxyFormMapper;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * MapStruct generates {@code *Impl} classes at compile time; register them for native images.
 */
@Configuration
@ImportRuntimeHints(MapStructNativeConfiguration.Hints.class)
public class MapStructNativeConfiguration {

    private static final String[] MAPPER_IMPLS = {
            "io.github.connellite.proxy.mapper.ProxyUserFormMapperImpl",
            "io.github.connellite.proxy.mapper.PasswordChangeFormMapperImpl",
            "io.github.connellite.proxy.mapper.UpstreamProxyFormMapperImpl",
            "io.github.connellite.proxy.mapper.AppSettingsMapperImpl",
            "io.github.connellite.proxy.mapper.TlsStatusMapperImpl"
    };

    static final class Hints implements RuntimeHintsRegistrar {

        private static final MemberCategory[] CATEGORIES = {
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS
        };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (Class<?> type : new Class<?>[] {
                    ProxyUserFormMapper.class,
                    PasswordChangeFormMapper.class,
                    UpstreamProxyFormMapper.class,
                    AppSettingsMapper.class,
                    TlsStatusMapper.class
            }) {
                hints.reflection().registerType(type, CATEGORIES);
            }
            for (String implName : MAPPER_IMPLS) {
                try {
                    hints.reflection().registerType(Class.forName(implName, false, classLoader), CATEGORIES);
                } catch (ClassNotFoundException ignored) {
                    // Not generated yet in this compile unit.
                }
            }
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class MapStructNativeConfiguration {
    private MapStructNativeConfiguration() {
    }
}
#endif
