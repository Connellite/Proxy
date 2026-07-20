package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Allow Spring Boot Hikari {@code spring.datasource.hikari.*} property binding
 * under GraalVM native images (reflective setter invocation).
 */
@Configuration
@ImportRuntimeHints(HikariNativeConfiguration.Hints.class)
public class HikariNativeConfiguration {

    static final class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            MemberCategory[] categories = {
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS
            };
            hints.reflection().registerType(HikariConfig.class, categories);
            hints.reflection().registerType(HikariDataSource.class, categories);
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class HikariNativeConfiguration {
    private HikariNativeConfiguration() {
    }
}
#endif
