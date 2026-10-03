package io.github.connellite.proxy.config.feature;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.graalvm.nativeimage.hosted.Feature;

import java.security.Security;

/**
 * GraalVM verifies a JCE provider only when {@link Security#addProvider} runs during image build.
 * SSHD adds BouncyCastle at runtime for X25519, which the native image then rejects.
 */
public final class BouncyCastleFeature implements Feature {

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
