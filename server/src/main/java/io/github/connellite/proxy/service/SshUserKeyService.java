package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.IssuedSshKey;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.SshUserKey;
import io.github.connellite.proxy.proxy.ssh.SshKeyMaterial;
import io.github.connellite.proxy.repository.SshUserKeyRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SshUserKeyService {

    static final int MAX_COMMENT_LENGTH = 256;
    static final int MAX_PASSPHRASE_LENGTH = 128;

    private final SshUserKeyRepository repository;
    private final ProxyUserService users;

    @Transactional(readOnly = true)
    public List<SshUserKey> list(String userId) {
        ProxyUser user = users.getRequired(userId);
        return repository.findByUser_IdOrderByCreatedAtAscIdAsc(user.getId());
    }

    @Transactional
    public IssuedSshKey issue(String userId, String comment, String passphrase) {
        ProxyUser user = users.getRequired(userId);
        String normalizedComment = normalizeComment(comment);
        if (passphrase != null && passphrase.length() > MAX_PASSPHRASE_LENGTH) {
            throw new IllegalArgumentException("Passphrase must be at most 128 characters");
        }
        boolean encrypted = StringUtils.isNotBlank(passphrase);
        String keyComment = normalizedComment == null ? user.getId() : normalizedComment;
        KeyPair pair;
        String publicLine;
        String fingerprint;
        String privatePem;
        try {
            pair = SshKeyMaterial.generateEd25519();
            publicLine = SshKeyMaterial.publicKeyLine(pair.getPublic());
            fingerprint = SshKeyMaterial.fingerprint(pair.getPublic());
            privatePem = SshKeyMaterial.privateKeyPem(pair, keyComment, encrypted ? passphrase : null);
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("Failed to generate SSH key", ex);
        }
        SshUserKey entity = new SshUserKey();
        entity.setUser(user);
        entity.setPublicKey(publicLine);
        entity.setFingerprint(fingerprint);
        entity.setComment(normalizedComment);
        repository.saveAndFlush(entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("Failed to store SSH key");
        }
        return new IssuedSshKey(entity.getId(), fingerprint, publicLine, privatePem, encrypted);
    }

    @Transactional
    public void revoke(String userId, long keyId) {
        ProxyUser user = users.getRequired(userId);
        SshUserKey key = repository.findByIdAndUser_Id(keyId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Key not found"));
        repository.delete(key);
    }

    private static String normalizeComment(String comment) {
        String trimmed = StringUtils.trimToNull(comment);
        if (trimmed == null) {
            return null;
        }
        trimmed = trimmed.replace('\r', ' ').replace('\n', ' ').trim();
        trimmed = StringUtils.trimToNull(trimmed);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment must be at most 256 characters");
        }
        return trimmed;
    }
}
