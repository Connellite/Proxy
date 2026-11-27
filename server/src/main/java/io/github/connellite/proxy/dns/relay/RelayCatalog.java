package io.github.connellite.proxy.dns.relay;

import com.google.common.net.InetAddresses;
import io.github.connellite.proxy.dns.upstream.UpstreamDnsEndpoint;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.model.RelayDnsAllow;
import io.github.connellite.proxy.model.RelayRoute;
import io.github.connellite.proxy.model.RelayRouteMode;
import io.github.connellite.proxy.model.RelayTcpAllow;
import io.github.connellite.proxy.repository.RelayDnsAllowRepository;
import io.github.connellite.proxy.repository.RelayRouteRepository;
import io.github.connellite.proxy.repository.RelayTcpAllowRepository;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.util.AllowNetworks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Hot path for DNS and TCP. Rebuilt from the table after a commit. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayCatalog {

    private final RelayRouteRepository repository;
    private final RelayDnsAllowRepository dnsAllows;
    private final RelayTcpAllowRepository tcpAllows;
    private final SettingsService settingsService;
    private final PlatformTransactionManager transactionManager;

    private volatile RelaySnapshot snapshot = RelaySnapshot.defaults();

    public RelaySnapshot current() {
        return snapshot;
    }

    public void reload() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        RelaySnapshot loaded = template.execute(status -> build(
                repository.findAll(),
                dnsAllows.findAllByOrderByCidrAsc(),
                tcpAllows.findAllByOrderByCidrAsc(),
                settingsService.get()));
        if (loaded != null) {
            snapshot = loaded;
        }
    }

    static RelaySnapshot build(List<RelayRoute> rows, List<RelayDnsAllow> dnsRows, List<RelayTcpAllow> tcpRows, AppSettings settings) {
        InetAddress advertised = parseAddress(settings.getRelayAdvertisedAddress());
        UpstreamDnsEndpoint resolver = resolver(settings.getRelayUpstreamResolver());
        List<RelaySnapshot.Route> routes = new ArrayList<>();
        for (RelayRoute row : rows) {
            if (!row.isEnabled() || row.getName() == null || row.getMode() == null) {
                continue;
            }
            InetAddress answer = null;
            if (row.getMode() == RelayRouteMode.ADDRESS) {
                answer = parseAddress(row.getAnswerAddress());
                if (answer == null) {
                    continue;
                }
            }
            routes.add(new RelaySnapshot.Route(row.getName(), row.getMode(), row.getUpstreamHost(),
                    row.getUpstreamPort(), answer));
        }
        return RelaySnapshot.builder()
                .routes(routes)
                .advertisedAddress(advertised)
                .upstreamResolver(resolver)
                .dnsEnabled(settings.isRelayDnsEnabled())
                .dnsBindHost(settings.getRelayDnsBindHost())
                .dnsPort(settings.getRelayDnsPort())
                .tcpEnabled(settings.isRelayTcpEnabled())
                .tcpBindHost(settings.getRelayTcpBindHost())
                .tcpPort(settings.getRelayTcpPort())
                .dohEnabled(settings.isRelayDohEnabled())
                .dohBindHost(settings.getRelayDohBindHost())
                .dohPort(settings.getRelayDohPort())
                .doqEnabled(settings.isRelayDoqEnabled())
                .doqBindHost(settings.getRelayDoqBindHost())
                .doqPort(settings.getRelayDoqPort())
                .dotEnabled(settings.isRelayDotEnabled())
                .dotBindHost(settings.getRelayDotBindHost())
                .dotPort(settings.getRelayDotPort())
                .doh3Enabled(settings.isRelayDoh3Enabled())
                .doh3BindHost(settings.getRelayDoh3BindHost())
                .doh3Port(settings.getRelayDoh3Port())
                .dnsAllow(dnsNetworks(dnsRows))
                .tcpAllow(tcpNetworks(tcpRows))
                .tcpUseUpstream(settings.isRelayTcpUseUpstream())
                .build();
    }

    private static AllowNetworks dnsNetworks(List<RelayDnsAllow> rows) {
        return networks(rows == null ? List.of() : rows.stream().map(RelayDnsAllow::getCidr).toList());
    }

    private static AllowNetworks tcpNetworks(List<RelayTcpAllow> rows) {
        return networks(rows == null ? List.of() : rows.stream().map(RelayTcpAllow::getCidr).toList());
    }

    private static AllowNetworks networks(List<String> cidrs) {
        List<String> valid = new ArrayList<>();
        for (String cidr : cidrs) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            try {
                valid.add(AllowNetworks.canonical(cidr));
            } catch (IllegalArgumentException ex) {
                log.warn("Skipping invalid allowlist entry '{}': {}", cidr, ex.getMessage());
            }
        }
        return AllowNetworks.parseAll(valid);
    }

    private static UpstreamDnsEndpoint resolver(String raw) {
        try {
            return UpstreamDnsEndpoint.parse(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid upstream resolver '{}', using {}", raw, UpstreamDnsEndpoint.DEFAULT);
            return UpstreamDnsEndpoint.DEFAULT;
        }
    }

    static InetAddress parseAddress(String raw) {
        if (raw == null || raw.isBlank() || !InetAddresses.isInetAddress(raw.trim())) {
            return null;
        }
        InetAddress address = InetAddresses.forString(raw.trim());
        if (address.isAnyLocalAddress()) {
            return null;
        }
        return address;
    }
}
