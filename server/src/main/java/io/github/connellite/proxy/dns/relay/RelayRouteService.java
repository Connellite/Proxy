package io.github.connellite.proxy.dns.relay;

import io.github.connellite.proxy.dns.upstream.UpstreamDnsEndpoint;
import io.github.connellite.proxy.dto.AppSettings;
import io.github.connellite.proxy.model.RelayRoute;
import io.github.connellite.proxy.model.RelayRouteMode;
import io.github.connellite.proxy.repository.RelayRouteRepository;
import io.github.connellite.proxy.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.InetAddress;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RelayRouteService {

    private final RelayRouteRepository repository;
    private final SettingsService settingsService;
    private final RelayCatalog catalog;
    private final RelayRuntime runtime;

    @Transactional(readOnly = true)
    public List<RelayRoute> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional
    public void saveSettings(AppSettings form) {
        validateSettings(form);
        AppSettings current = settingsService.get();
        boolean dnsRestart = listenerChanged(current.isRelayDnsEnabled(), current.getRelayDnsBindHost(),
                current.getRelayDnsPort(), form.isRelayDnsEnabled(), form.getRelayDnsBindHost(), form.getRelayDnsPort());
        boolean tcpRestart = listenerChanged(current.isRelayTcpEnabled(), current.getRelayTcpBindHost(),
                current.getRelayTcpPort(), form.isRelayTcpEnabled(), form.getRelayTcpBindHost(), form.getRelayTcpPort());
        boolean dohRestart = listenerChanged(current.isRelayDohEnabled(), current.getRelayDohBindHost(),
                current.getRelayDohPort(), form.isRelayDohEnabled(), form.getRelayDohBindHost(), form.getRelayDohPort());
        boolean doqRestart = listenerChanged(current.isRelayDoqEnabled(), current.getRelayDoqBindHost(),
                current.getRelayDoqPort(), form.isRelayDoqEnabled(), form.getRelayDoqBindHost(), form.getRelayDoqPort());
        boolean dotRestart = listenerChanged(current.isRelayDotEnabled(), current.getRelayDotBindHost(),
                current.getRelayDotPort(), form.isRelayDotEnabled(), form.getRelayDotBindHost(), form.getRelayDotPort());
        boolean doh3Restart = listenerChanged(current.isRelayDoh3Enabled(), current.getRelayDoh3BindHost(),
                current.getRelayDoh3Port(), form.isRelayDoh3Enabled(), form.getRelayDoh3BindHost(), form.getRelayDoh3Port());
        current.setRelayDnsEnabled(form.isRelayDnsEnabled());
        current.setRelayDnsBindHost(form.getRelayDnsBindHost());
        current.setRelayDnsPort(form.getRelayDnsPort());
        current.setRelayTcpEnabled(form.isRelayTcpEnabled());
        current.setRelayTcpBindHost(form.getRelayTcpBindHost());
        current.setRelayTcpPort(form.getRelayTcpPort());
        current.setRelayTcpUseUpstream(form.isRelayTcpUseUpstream());
        current.setRelayDohEnabled(form.isRelayDohEnabled());
        current.setRelayDohBindHost(form.getRelayDohBindHost());
        current.setRelayDohPort(form.getRelayDohPort());
        current.setRelayDoqEnabled(form.isRelayDoqEnabled());
        current.setRelayDoqBindHost(form.getRelayDoqBindHost());
        current.setRelayDoqPort(form.getRelayDoqPort());
        current.setRelayDotEnabled(form.isRelayDotEnabled());
        current.setRelayDotBindHost(form.getRelayDotBindHost());
        current.setRelayDotPort(form.getRelayDotPort());
        current.setRelayDoh3Enabled(form.isRelayDoh3Enabled());
        current.setRelayDoh3BindHost(form.getRelayDoh3BindHost());
        current.setRelayDoh3Port(form.getRelayDoh3Port());
        current.setRelayAdvertisedAddress(StringUtils.trimToNull(form.getRelayAdvertisedAddress()));
        current.setRelayUpstreamResolver(form.getRelayUpstreamResolver());
        settingsService.save(current);
        afterCommit(dnsRestart, tcpRestart, dohRestart, doqRestart, dotRestart, doh3Restart);
    }

    @Transactional
    public RelayRoute saveRoute(RelayRoute form) {
        String name = RelayNames.normalizePattern(form.getName());
        RelayRouteMode mode = form.getMode();
        if (mode == null) {
            throw new IllegalArgumentException("Mode is required");
        }
        AppSettings settings = settingsService.get();
        String upstreamHost = null;
        Integer upstreamPort = null;
        String answer = null;
        if (mode == RelayRouteMode.RELAY) {
            if (RelayCatalog.parseAddress(settings.getRelayAdvertisedAddress()) == null) {
                throw new IllegalArgumentException("Set the address clients should receive before adding a relay route");
            }
            upstreamHost = normalizeHost(form.getUpstreamHost());
            if (upstreamHost != null) {
                InetAddress upstreamAddress = RelayCatalog.parseAddress(upstreamHost);
                InetAddress advertised = RelayCatalog.parseAddress(settings.getRelayAdvertisedAddress());
                if (upstreamAddress != null && upstreamAddress.equals(advertised)) {
                    String msg =  "That address is this server. Leave the upstream host empty to use the real address of the name";
                    throw new IllegalArgumentException(msg);
                }
            }
            upstreamPort = form.getUpstreamPort();
            if (upstreamPort != null) {
                HostPort.parsePort(Integer.toString(upstreamPort));
            }
        } else {
            InetAddress ip = RelayCatalog.parseAddress(form.getAnswerAddress());
            if (ip == null) {
                throw new IllegalArgumentException("Address mode requires a single IP");
            }
            answer = ip.getHostAddress();
        }
        RelayRoute row = form.getId() == null
                ? new RelayRoute()
                : repository.findById(form.getId()).orElseThrow(() -> new IllegalArgumentException("Route not found"));
        for (RelayRoute other : repository.findAll()) {
            if (name.equals(other.getName()) && (row.getId() == null || !row.getId().equals(other.getId()))) {
                throw new IllegalArgumentException("Name already listed: " + name);
            }
        }
        row.setName(name);
        row.setMode(mode);
        row.setUpstreamHost(upstreamHost);
        row.setUpstreamPort(upstreamPort);
        row.setAnswerAddress(answer);
        row.setEnabled(form.isEnabled());
        RelayRoute saved = repository.save(row);
        afterCommit(false, false, false, false, false, false);
        return saved;
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Route not found");
        }
        repository.deleteById(id);
        afterCommit(false, false, false, false, false, false);
    }

    private void afterCommit(boolean dnsRestart, boolean tcpRestart, boolean dohRestart, boolean doqRestart,
                             boolean dotRestart, boolean doh3Restart) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                catalog.reload();
                if (dnsRestart) {
                    runtime.restartDns();
                }
                if (tcpRestart) {
                    runtime.restartTcp();
                }
                if (dohRestart) {
                    runtime.restartDoh();
                }
                if (doqRestart) {
                    runtime.restartDoq();
                }
                if (dotRestart) {
                    runtime.restartDot();
                }
                if (doh3Restart) {
                    runtime.restartDoh3();
                }
            }
        });
    }

    private static void validateSettings(AppSettings form) {
        form.setRelayDnsBindHost(requiredHost(form.getRelayDnsBindHost(), "DNS bind address"));
        form.setRelayTcpBindHost(requiredHost(form.getRelayTcpBindHost(), "TCP bind address"));
        form.setRelayDnsPort(HostPort.parsePort(Integer.toString(form.getRelayDnsPort())));
        form.setRelayTcpPort(HostPort.parsePort(Integer.toString(form.getRelayTcpPort())));
        form.setRelayDohPort(HostPort.parsePort(Integer.toString(form.getRelayDohPort())));
        form.setRelayDoqPort(HostPort.parsePort(Integer.toString(form.getRelayDoqPort())));
        form.setRelayDohBindHost(requiredHost(form.getRelayDohBindHost(), "DoH bind address"));
        form.setRelayDoqBindHost(requiredHost(form.getRelayDoqBindHost(), "DoQ bind address"));
        form.setRelayDotPort(HostPort.parsePort(Integer.toString(form.getRelayDotPort())));
        form.setRelayDotBindHost(requiredHost(form.getRelayDotBindHost(), "DoT bind address"));
        form.setRelayDoh3Port(HostPort.parsePort(Integer.toString(form.getRelayDoh3Port())));
        form.setRelayDoh3BindHost(requiredHost(form.getRelayDoh3BindHost(), "DoH3 bind address"));
        if (sameEndpoint(form.getRelayTcpBindHost(), form.getRelayTcpPort(),
                form.getRelayDohBindHost(), form.getRelayDohPort())) {
            throw new IllegalArgumentException("DoH and the TCP relay cannot listen on the same address and port");
        }
        if (sameEndpoint(form.getRelayTcpBindHost(), form.getRelayTcpPort(),
                form.getRelayDotBindHost(), form.getRelayDotPort())
                || sameEndpoint(form.getRelayDohBindHost(), form.getRelayDohPort(),
                form.getRelayDotBindHost(), form.getRelayDotPort())) {
            throw new IllegalArgumentException("DoT cannot listen on the same address and port as DoH or the TCP relay");
        }
        if (sameEndpoint(form.getRelayDoh3BindHost(), form.getRelayDoh3Port(),
                form.getRelayDoqBindHost(), form.getRelayDoqPort())) {
            throw new IllegalArgumentException("DoH3 and DoQ cannot listen on the same address and port");
        }
        UpstreamDnsEndpoint.parse(form.getRelayUpstreamResolver());
        if (StringUtils.isNotBlank(form.getRelayAdvertisedAddress())
                && RelayCatalog.parseAddress(form.getRelayAdvertisedAddress()) == null) {
            throw new IllegalArgumentException("Client address must be a single IP");
        }
        form.setRelayAdvertisedAddress(StringUtils.trimToNull(form.getRelayAdvertisedAddress()));
    }

    private static String requiredHost(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return raw.trim();
    }

    private static String normalizeHost(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String host = raw.trim();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            throw new IllegalArgumentException("Put the upstream port in its own field");
        }
        return host;
    }

    static boolean sameEndpoint(String host, int port, String otherHost, int otherPort) {
        return port == otherPort && host != null && otherHost != null && host.equalsIgnoreCase(otherHost);
    }

    private static boolean listenerChanged(boolean enabled, String host, int port,
                                           boolean nextEnabled, String nextHost, int nextPort) {
        return enabled != nextEnabled || port != nextPort || !String.valueOf(host).equals(String.valueOf(nextHost));
    }
}
