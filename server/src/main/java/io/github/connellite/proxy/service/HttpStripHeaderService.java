package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.HttpStripHeader;
import io.github.connellite.proxy.repository.HttpStripHeaderRepository;
#if SPRING_BOOT_3
import jakarta.annotation.PostConstruct;
#else
import javax.annotation.PostConstruct;
#endif
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class HttpStripHeaderService {

    private static final List<String> DEFAULT_HEADERS = List.of(
            "Via",
            "X-Forwarded-For",
            "Forwarded");

    private final HttpStripHeaderRepository repository;

    /** Lower-case header names for Netty paths — no DB access. */
    private volatile Set<String> cachedNames = Set.of();

    @PostConstruct
    void loadOnStartup() {
        ensureDefaults();
        refreshCache();
    }

    @Transactional(readOnly = true)
    public List<HttpStripHeader> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    /**
     * Lower-case header names for case-insensitive removal. Non-blocking for Netty.
     */
    public Set<String> currentNamesLowerCase() {
        return cachedNames;
    }

    @Transactional
    public HttpStripHeader add(String rawName) {
        String name = normalize(rawName);
        if (repository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Header already listed: " + name);
        }
        HttpStripHeader saved = repository.save(new HttpStripHeader(name));
        refreshCache();
        return saved;
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Strip header not found: " + id);
        }
        repository.deleteById(id);
        refreshCache();
    }

    @Transactional
    public void ensureDefaults() {
        if (repository.count() > 0) {
            return;
        }
        for (String name : DEFAULT_HEADERS) {
            if (!repository.existsByNameIgnoreCase(name)) {
                repository.save(new HttpStripHeader(name));
            }
        }
    }

    @Transactional(readOnly = true)
    public void refreshCache() {
        Set<String> next = ConcurrentHashMap.newKeySet();
        for (HttpStripHeader header : repository.findAll()) {
            String name = StringUtils.trimToNull(header.getName());
            if (name != null) {
                next.add(name.toLowerCase(Locale.ROOT));
            }
        }
        cachedNames = Collections.unmodifiableSet(next);
    }

    static String normalize(String rawName) {
        String name = StringUtils.trimToNull(rawName);
        if (name == null) {
            throw new IllegalArgumentException("Header name is required");
        }
        if (name.length() > 256) {
            throw new IllegalArgumentException("Header name is too long");
        }
        if (name.indexOf(':') >= 0 || name.indexOf(' ') >= 0 || name.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("Header name must be a single token without ':' or spaces");
        }
        return name;
    }
}
