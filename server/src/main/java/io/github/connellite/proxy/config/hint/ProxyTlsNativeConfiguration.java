package io.github.connellite.proxy.config.hint;

#if SPRING_BOOT_3
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * {@link io.github.connellite.proxy.proxy.http.ProxyTlsService} converts PEM keys through
 * BouncyCastle {@code KeyFactory} SPI. GraalVM drops those provider classes unless registered.
 */
@Configuration
@ImportRuntimeHints(ProxyTlsNativeConfiguration.Hints.class)
public class ProxyTlsNativeConfiguration {

    static final class Hints implements RuntimeHintsRegistrar {

        private static final MemberCategory[] TYPE_CATEGORIES = {
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS
        };

        /** BC {@code KeyFactory} / asymmetric configurators used for RSA and EC PEM keys. */
        private static final String[] BC_KEY_FACTORY_TYPES = {
                "org.bouncycastle.jcajce.provider.asymmetric.RSA",
                "org.bouncycastle.jcajce.provider.asymmetric.EC",
                "org.bouncycastle.jcajce.provider.asymmetric.EdEC",
                "org.bouncycastle.jcajce.provider.asymmetric.rsa.KeyFactorySpi",
                "org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi",
                "org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi$EC",
                "org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi$ECDSA",
                "org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi$ECDH",
                "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi",
                "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$Ed25519",
                "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$Ed448",
                "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$EdDSA",
                "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$X25519",
                "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$X448",
                "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$XDH"
        };

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(BouncyCastleProvider.class, TYPE_CATEGORIES);
            hints.reflection().registerType(PEMParser.class, TYPE_CATEGORIES);
            hints.reflection().registerType(JcaPEMKeyConverter.class, TYPE_CATEGORIES);

            for (String typeName : BC_KEY_FACTORY_TYPES) {
                registerTypeName(hints, classLoader, typeName);
            }

            hints.resources().registerPattern("org/bouncycastle/x509/CertPathReviewerMessages*.properties");
        }

        private static void registerTypeName(RuntimeHints hints, ClassLoader classLoader, String className) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                hints.reflection().registerType(type, TYPE_CATEGORIES);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Optional / multi-release class missing on this JDK — skip.
            }
        }
    }
}
#else
/** No-op placeholder for Spring Boot 2 builds. */
public final class ProxyTlsNativeConfiguration {
    private ProxyTlsNativeConfiguration() {
    }
}
#endif
