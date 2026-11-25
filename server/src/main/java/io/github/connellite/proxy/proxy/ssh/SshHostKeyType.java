package io.github.connellite.proxy.proxy.ssh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.security.SecurityUtils;

/**
 * SSH host key types the server can generate. Bound from {@code proxy.ssh.host-keys} by the SSH name,
 * e.g. {@code ecdsa-sha2-nistp256}. Each type is stored in its own file under the data directory.
 */
@Getter
@RequiredArgsConstructor
public enum SshHostKeyType {

    SSH_ED25519(KeyPairProvider.SSH_ED25519, SecurityUtils.ED25519, 0, "ssh-hostkey-ed25519.ser"),
    ECDSA_SHA2_NISTP256(KeyPairProvider.ECDSA_SHA2_NISTP256, KeyUtils.EC_ALGORITHM, 256, "ssh-hostkey-ecdsa-nistp256.ser"),
    ECDSA_SHA2_NISTP384(KeyPairProvider.ECDSA_SHA2_NISTP384, KeyUtils.EC_ALGORITHM, 384, "ssh-hostkey-ecdsa-nistp384.ser"),
    //Keeps the file name used before host key types were configurable
    ECDSA_SHA2_NISTP521(KeyPairProvider.ECDSA_SHA2_NISTP521, KeyUtils.EC_ALGORITHM, 521, "ssh-hostkey.ser"),
    SSH_RSA(KeyPairProvider.SSH_RSA, KeyUtils.RSA_ALGORITHM, 3072, "ssh-hostkey-rsa.ser");

    private final String sshName;
    private final String algorithm;
    private final int keySize;
    private final String fileName;
}
