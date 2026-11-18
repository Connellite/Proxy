package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.model.SftpAccess;
import io.github.connellite.proxy.proxy.sftp.SftpGrant;
import io.github.connellite.proxy.repository.ProxyUserRepository;
import io.github.connellite.proxy.repository.SftpAccessRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Per-user SFTP folders ({@link SftpAccess}). */
@Service
@RequiredArgsConstructor
public class SftpAccessService {

    private final SftpAccessRepository repository;
    private final ProxyUserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SftpAccess> findAll() {
        return repository.findAll();
    }

    /**
     * Grants SFTP access or changes it. A blank {@code path} removes access. Otherwise the path must
     * be an existing absolute directory and is stored in its real (resolved) form.
     */
    @Transactional
    public void save(String userId, String path, boolean readOnly) {
        ProxyUser user = userRepository.findById(StringUtils.trimToEmpty(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        String root = normalizeRoot(path);
        if (root == null) {
            remove(user.getId());
            return;
        }
        SftpAccess access = repository.findById(user.getId()).orElseGet(() -> {
            SftpAccess created = new SftpAccess();
            created.setUser(user);
            return created;
        });
        access.setRootPath(root);
        access.setReadOnly(readOnly);
        repository.save(access);
    }

    @Transactional
    public void remove(String userId) {
        if (userId != null && repository.existsById(userId)) {
            repository.deleteById(userId);
        }
    }

    /** The user's grant if one is set and the folder still exists. */
    @Transactional(readOnly = true)
    public Optional<SftpGrant> findGrant(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findById(userId)
                .filter(access -> StringUtils.isNotBlank(access.getRootPath()))
                .map(access -> new SftpGrant(Path.of(access.getRootPath()), access.isReadOnly()))
                .filter(grant -> Files.isDirectory(grant.root()));
    }

    static String normalizeRoot(String value) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        Path path;
        try {
            path = Path.of(trimmed);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid folder path: " + trimmed);
        }
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("SFTP folder must be an absolute path: " + trimmed);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("SFTP folder does not exist or is not a directory: " + trimmed);
        }
        String real;
        try {
            real = path.toRealPath().toString();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot access SFTP folder: " + trimmed);
        }
        if (real.length() > 1024) {
            throw new IllegalArgumentException("SFTP folder path must be at most 1024 characters");
        }
        return real;
    }
}
