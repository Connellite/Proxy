package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyAllow;
import io.github.connellite.proxy.repository.ProxyAllowRepository;
import io.github.connellite.proxy.util.AllowNetworks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProxyAllowService {

    private final ProxyAllowRepository repository;
    private final ProxyAllowCatalog catalog;

    @Transactional(readOnly = true)
    public List<ProxyAllow> allows() {
        return repository.findAllByOrderByCidrAsc();
    }

    @Transactional
    public void save(String raw) {
        String cidr = AllowNetworks.canonical(raw);
        if (repository.existsByCidr(cidr)) {
            throw new IllegalArgumentException("Network already listed: " + cidr);
        }
        ProxyAllow row = new ProxyAllow();
        row.setCidr(cidr);
        repository.save(row);
        afterCommit();
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Network not found");
        }
        repository.deleteById(id);
        afterCommit();
    }

    private void afterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                catalog.reload();
            }
        });
    }
}
