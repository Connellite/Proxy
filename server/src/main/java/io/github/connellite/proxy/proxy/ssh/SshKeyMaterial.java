package io.github.connellite.proxy.proxy.ssh;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Ed25519 key generation and OpenSSH encoding. Private key bytes are never written to disk or logs here.
 */
@UtilityClass
public final class SshKeyMaterial {

    public static KeyPair generateEd25519() throws GeneralSecurityException {
        KeyPairGenerator generator = SecurityUtils.getKeyPairGenerator(SecurityUtils.ED25519);
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    public static String publicKeyLine(PublicKey key) throws IOException {
        StringBuilder line = new StringBuilder(128);
        PublicKeyEntry.appendPublicKeyEntry(line, key);
        return line.toString();
    }

    /**
     * OpenSSH SHA-256 fingerprint of the SSH wire public key ({@code SHA256:...}).
     * Uses {@link MessageDigest} directly. SSHD's {@code BuiltinDigests.sha256} records
     * support once at class init, and that flag stays false in a native image.
     */
    public static String fingerprint(PublicKey key) {
        if (key == null) {
            return null;
        }
        try {
            ByteArrayBuffer buffer = new ByteArrayBuffer();
            buffer.putRawPublicKey(key);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(buffer.array(), 0, buffer.wpos());
            String encoded = Base64.getEncoder().encodeToString(digest.digest()).replace("=", "");
            return "SHA256:" + encoded;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 fingerprint digest is not available", ex);
        }
    }

    public static String privateKeyPem(KeyPair pair, String comment, String passphrase)
            throws IOException, GeneralSecurityException {
        OpenSSHKeyEncryptionContext options = null;
        if (StringUtils.isNotBlank(passphrase)) {
            options = new OpenSSHKeyEncryptionContext();
            options.setCipherType("256");
            options.setPassword(passphrase);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(pair, comment, options, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    public static boolean matches(String storedLine, PublicKey presented) {
        if (presented == null || StringUtils.isBlank(storedLine)) {
            return false;
        }
        try {
            AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(storedLine);
            if (entry == null) {
                return false;
            }
            PublicKey stored = entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING);
            return stored != null && KeyUtils.compareKeys(presented, stored);
        } catch (RuntimeException | IOException | GeneralSecurityException ex) {
            return false;
        }
    }
}
