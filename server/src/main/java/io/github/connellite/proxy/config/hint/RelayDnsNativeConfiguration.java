package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;
import org.xbill.DNS.UNKRecord;

/**
 * dnsjava builds records by type. The classes a relay answer or a forwarded packet can touch
 * must stay reachable in a native image.
 */
@Configuration
@ImportRuntimeHints(RelayDnsNativeConfiguration.Hints.class)
public class RelayDnsNativeConfiguration {

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
            for (Class<?> type : new Class<?>[] {
                    Message.class, Header.class, Name.class, Record.class, ARecord.class, AAAARecord.class,
                    UNKRecord.class, Type.class, DClass.class, Rcode.class, Section.class, SimpleResolver.class
            }) {
                hints.reflection().registerType(type, categories);
            }
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class RelayDnsNativeConfiguration {
    private RelayDnsNativeConfiguration() {
    }
}
#endif
