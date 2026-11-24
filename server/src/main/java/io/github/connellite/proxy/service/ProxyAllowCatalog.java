package io.github.connellite.proxy.service;

import io.github.connellite.proxy.model.ProxyAllow;
import io.github.connellite.proxy.repository.ProxyAllowRepository;
import io.github.connellite.proxy.util.AllowNetworks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Hot path for proxy accepts. Rebuilt from {@code PROXY_ALLOW} after a commit. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyAllowCatalog {

    private final ProxyAllowRepository repository;
    private final PlatformTransactionManager transactionManager;

    private volatile AllowNetworks networks = AllowNetworks.everyone();

    public boolean allows(InetAddress address) {
        return networks.allows(address);
    }

    public void reload() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        AllowNetworks loaded = template.execute(status -> networks(repository.findAllByOrderByCidrAsc()));
        if (loaded != null) {
            networks = loaded;
        }
    }

    private static AllowNetworks networks(List<ProxyAllow> rows) {
        List<String> valid = new ArrayList<>();
        if (rows != null) {
            for (ProxyAllow row : rows) {
                String cidr = row.getCidr();
                if (cidr == null || cidr.isBlank()) {
                    continue;
                }
                try {
                    valid.add(AllowNetworks.canonical(cidr));
                } catch (IllegalArgumentException ex) {
                    log.warn("Skipping invalid allowlist entry '{}': {}", cidr, ex.getMessage());
                }
            }
        }
        return AllowNetworks.parseAll(valid);
    }
}
