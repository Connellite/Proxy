package io.github.connellite.proxy.service;

import io.github.connellite.proxy.dto.UpstreamProxyForm;
import io.github.connellite.proxy.dto.UpstreamSnapshot;
import io.github.connellite.proxy.model.UpstreamProxy;
import io.github.connellite.proxy.model.UpstreamProxyType;
import io.github.connellite.proxy.proxy.ssh.SshUpstreamClient;
import io.github.connellite.proxy.repository.UpstreamProxyRepository;
#if SPRING_BOOT_3
import jakarta.annotation.PostConstruct;
#else
import javax.annotation.PostConstruct;
#endif
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UpstreamProxyService {

    private final UpstreamProxyRepository repository;
    private final SshUpstreamClient sshUpstreamClient;

    /** Live selection for outbound connectors; updated on every mutating admin action. */
    private volatile UpstreamSnapshot selectedSnapshot;

    @PostConstruct
    void loadSelectedOnStartup() {
        refreshSelectedSnapshot();
    }

    @Transactional(readOnly = true)
    public List<UpstreamProxy> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public UpstreamProxy getRequired(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Upstream proxy not found: " + id));
    }

    /**
     * Non-blocking read for Netty outbound paths — does not touch the database.
     */
    public Optional<UpstreamSnapshot> currentSelected() {
        return Optional.ofNullable(selectedSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<UpstreamProxy> findSelected() {
        return repository.findFirstBySelectedTrue();
    }

    @Transactional
    public UpstreamProxy create(UpstreamProxyForm form) {
        UpstreamProxy proxy = new UpstreamProxy();
        applyForm(proxy, form, true);
        return repository.save(proxy);
    }

    @Transactional
    public UpstreamProxy update(Long id, UpstreamProxyForm form) {
        UpstreamProxy proxy = getRequired(id);
        applyForm(proxy, form, false);
        UpstreamProxy saved = repository.save(proxy);
        sshUpstreamClient.invalidate(saved.getId());
        if (saved.isSelected()) {
            selectedSnapshot = toSnapshot(saved);
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        boolean wasSelected = repository.findById(id).map(UpstreamProxy::isSelected).orElse(false);
        repository.deleteById(id);
        sshUpstreamClient.invalidate(id);
        if (wasSelected) {
            selectedSnapshot = null;
        }
    }

    @Transactional
    public void select(Long id) {
        UpstreamProxy chosen = null;
        for (UpstreamProxy proxy : repository.findAll()) {
            boolean match = proxy.getId().equals(id);
            proxy.setSelected(match);
            if (match) {
                chosen = proxy;
            }
        }
        if (chosen == null) {
            throw new IllegalArgumentException("Upstream proxy not found: " + id);
        }
        sshUpstreamClient.invalidateAll();
        selectedSnapshot = toSnapshot(chosen);
    }

    @Transactional
    public void clearSelection() {
        for (UpstreamProxy proxy : repository.findAll()) {
            proxy.setSelected(false);
        }
        sshUpstreamClient.invalidateAll();
        selectedSnapshot = null;
    }

    @Transactional(readOnly = true)
    public void refreshSelectedSnapshot() {
        selectedSnapshot = repository.findFirstBySelectedTrue()
                .map(UpstreamProxyService::toSnapshot)
                .orElse(null);
    }

    private void applyForm(UpstreamProxy proxy, UpstreamProxyForm form, boolean creating) {
        String name = StringUtils.trimToNull(form.getName());
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }
        String host = StringUtils.trimToNull(form.getHost());
        if (host == null) {
            throw new IllegalArgumentException("Host is required");
        }
        if (form.getPort() < 1 || form.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        UpstreamProxyType type = form.getType() == null ? UpstreamProxyType.HTTP : form.getType();
        String username = StringUtils.trimToNull(form.getUsername());
        if (type == UpstreamProxyType.SSH && username == null) {
            throw new IllegalArgumentException("Username is required for SSH upstream");
        }
        proxy.setName(name);
        proxy.setType(type);
        proxy.setHost(host);
        proxy.setPort(form.getPort());
        proxy.setUsername(username);
        if (creating || form.isUpdatePassword()) {
            proxy.setPassword(StringUtils.trimToNull(form.getPassword()));
        }
        if (proxy.getUsername() == null) {
            proxy.setPassword(null);
        }
    }

    private static UpstreamSnapshot toSnapshot(UpstreamProxy proxy) {
        return new UpstreamSnapshot(
                proxy.getId(),
                proxy.getType() == null ? UpstreamProxyType.HTTP : proxy.getType(),
                proxy.getHost(),
                proxy.getPort(),
                proxy.getUsername(),
                proxy.getPassword());
    }
}
