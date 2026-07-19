package io.github.connellite.proxy.config;

#if SPRING_BOOT_3
import io.github.connellite.proxy.dto.EncryptionForm;
import io.github.connellite.proxy.dto.PasswordChangeForm;
import io.github.connellite.proxy.dto.ProxyUserForm;
import io.github.connellite.proxy.dto.SettingsForm;
import io.github.connellite.proxy.dto.TlsStatus;
import io.github.connellite.proxy.model.AdminAccount;
import io.github.connellite.proxy.model.AppSettings;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.service.UserThroughput;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.thymeleaf.expression.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf/SpEL reads model beans and collection helpers via reflection.
 * Register those types for GraalVM native images.
 */
@Configuration
@ImportRuntimeHints(ThymeleafNativeConfiguration.Hints.class)
public class ThymeleafNativeConfiguration {

    static final class Hints implements RuntimeHintsRegistrar {

        private static final MemberCategory[] CATEGORIES = {
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS
        };

        private static final Class<?>[] REFLECTION_TYPES = {
                FormatHelper.class,
                TlsStatus.class,
                EncryptionForm.class,
                SettingsForm.class,
                ProxyUserForm.class,
                PasswordChangeForm.class,
                ProxyUser.class,
                AdminAccount.class,
                AppSettings.class,
                UserThroughput.class,
                Lists.class,
                ArrayList.class,
                HashMap.class,
                List.class,
                Map.class,
                Collection.class
        };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (Class<?> type : REFLECTION_TYPES) {
                hints.reflection().registerType(type, CATEGORIES);
            }
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class ThymeleafNativeConfiguration {
    private ThymeleafNativeConfiguration() {
    }
}
#endif
