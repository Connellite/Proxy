package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import org.hibernate.community.dialect.SQLiteDialect;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Keep Hibernate SQLite dialect reachable for GraalVM native images.
 * The dialect is selected by a filtered string in {@code application.yml},
 * so AOT/native analysis would otherwise drop the class.
 */
@Configuration
@ImportRuntimeHints(HibernateNativeConfiguration.Hints.class)
public class HibernateNativeConfiguration {

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
            hints.reflection().registerType(SQLiteDialect.class, categories);
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class HibernateNativeConfiguration {
    private HibernateNativeConfiguration() {
    }
}
#endif
