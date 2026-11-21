package io.github.connellite.proxy.relay;

import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.model.RelayRoute;
import io.github.connellite.proxy.model.RelayRouteMode;
import io.github.connellite.proxy.repository.RelayRouteRepository;
import io.github.connellite.proxy.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/** Hot path for DNS and TCP. Rebuilt from the table after a commit. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayCatalog {

    private final RelayRouteRepository repository;
    private final SettingsService settingsService;
    private final PlatformTransactionManager transactionManager;

    private volatile RelaySnapshot snapshot = RelaySnapshot.defaults();

    public RelaySnapshot current() {
        return snapshot;
    }

    public void reload() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        RelaySnapshot loaded = template.execute(status -> build(repository.findAll(), settingsService.get()));
        if (loaded != null) {
            snapshot = loaded;
        }
    }

    static RelaySnapshot build(List<RelayRoute> rows, AppSettings settings) {
        InetAddress advertised = parseAddress(settings.getRelayAdvertisedAddress());
        HostPort resolver = resolver(settings.getRelayUpstreamResolver());
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
        return new RelaySnapshot(routes, advertised, resolver.getHost(), resolver.getPort(),
                settings.isRelayDnsEnabled(), settings.getRelayDnsBindHost(), settings.getRelayDnsPort(),
                settings.isRelayTcpEnabled(), settings.getRelayTcpBindHost(), settings.getRelayTcpPort());
    }

    private static HostPort resolver(String raw) {
        if (raw == null || raw.isBlank()) {
            return new HostPort("1.1.1.1", 53);
        }
        try {
            return HostPort.parse(raw, 53);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid upstream resolver '{}', using 1.1.1.1:53", raw);
            return new HostPort("1.1.1.1", 53);
        }
    }

    static InetAddress parseAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        boolean v4 = value.indexOf(':') < 0 && value.chars().allMatch(c -> c == '.' || (c >= '0' && c <= '9'));
        boolean v6 = value.indexOf(':') >= 0 && value.indexOf('.') < 0
                && value.chars().allMatch(c -> c == ':' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'));
        if (!v4 && !v6) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address.isAnyLocalAddress()) {
                return null;
            }
            return address;
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
