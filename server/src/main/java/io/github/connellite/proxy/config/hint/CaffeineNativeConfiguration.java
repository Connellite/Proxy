package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * A size-bounded Caffeine cache is the generated class {@code SSMS}, loaded by name.
 * A native image drops it unless it is registered.
 */
@Configuration
@ImportRuntimeHints(CaffeineNativeConfiguration.Hints.class)
public class CaffeineNativeConfiguration {

    /** Generated Caffeine classes loaded by name. A size-bounded cache is {@code SSMS}. */
    private static final String[] CACHE_CLASSES = {
            "com.github.benmanes.caffeine.cache.SSMS"
    };

    static final class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (String className : CACHE_CLASSES) {
                try {
                    hints.reflection().registerType(
                            Class.forName(className, false, classLoader),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.DECLARED_FIELDS,
                            MemberCategory.INVOKE_DECLARED_METHODS);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("Caffeine cache class is missing: " + className, ex);
                }
            }
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class CaffeineNativeConfiguration {
    private CaffeineNativeConfiguration() {
    }
}
#endif
