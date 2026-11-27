package io.github.connellite.proxy.dns.relay;

import io.github.connellite.proxy.dns.upstream.UpstreamDnsEndpoint;
import io.github.connellite.proxy.util.AllowNetworks;
import io.github.connellite.proxy.model.RelayRouteMode;
import lombok.Builder;
import lombok.Getter;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable working copy. DNS and new accepts read this; open connections do not.
 */
@Builder
public record RelaySnapshot(List<Route> routes,
                            InetAddress advertisedAddress,
                            UpstreamDnsEndpoint upstreamResolver,
                            boolean dnsEnabled,
                            String dnsBindHost,
                            int dnsPort,
                            boolean tcpEnabled,
                            String tcpBindHost,
                            int tcpPort,
                            boolean dohEnabled,
                            String dohBindHost,
                            int dohPort,
                            boolean doqEnabled,
                            String doqBindHost,
                            int doqPort,
                            boolean dotEnabled,
                            String dotBindHost,
                            int dotPort,
                            boolean doh3Enabled,
                            String doh3BindHost,
                            int doh3Port,
                            AllowNetworks dnsAllow,
                            AllowNetworks tcpAllow,
                            boolean tcpUseUpstream) {

    public static final int ANSWER_TTL_SECONDS = 30;
    public static final int MAX_FORWARDS = 32;

    public RelaySnapshot(List<Route> routes, InetAddress advertisedAddress, UpstreamDnsEndpoint upstreamResolver,
                         boolean dnsEnabled, String dnsBindHost, int dnsPort,
                         boolean tcpEnabled, String tcpBindHost, int tcpPort,
                         boolean dohEnabled, String dohBindHost, int dohPort,
                         boolean doqEnabled, String doqBindHost, int doqPort,
                         boolean dotEnabled, String dotBindHost, int dotPort,
                         boolean doh3Enabled, String doh3BindHost, int doh3Port,
                         AllowNetworks dnsAllow, AllowNetworks tcpAllow, boolean tcpUseUpstream) {
        this.routes = routes == null ? List.of() : List.copyOf(routes);
        this.advertisedAddress = advertisedAddress;
        this.upstreamResolver = upstreamResolver == null ? UpstreamDnsEndpoint.DEFAULT : upstreamResolver;
        this.dnsEnabled = dnsEnabled;
        this.dnsBindHost = dnsBindHost;
        this.dnsPort = dnsPort;
        this.tcpEnabled = tcpEnabled;
        this.tcpBindHost = tcpBindHost;
        this.tcpPort = tcpPort;
        this.dohEnabled = dohEnabled;
        this.dohBindHost = dohBindHost;
        this.dohPort = dohPort;
        this.doqEnabled = doqEnabled;
        this.doqBindHost = doqBindHost;
        this.doqPort = doqPort;
        this.dotEnabled = dotEnabled;
        this.dotBindHost = dotBindHost;
        this.dotPort = dotPort;
        this.doh3Enabled = doh3Enabled;
        this.doh3BindHost = doh3BindHost;
        this.doh3Port = doh3Port;
        this.dnsAllow = dnsAllow == null ? AllowNetworks.everyone() : dnsAllow;
        this.tcpAllow = tcpAllow == null ? AllowNetworks.everyone() : tcpAllow;
        this.tcpUseUpstream = tcpUseUpstream;
    }

    public static RelaySnapshot defaults() {
        return RelaySnapshot.builder()
                .routes(List.of())
                .upstreamResolver(UpstreamDnsEndpoint.DEFAULT)
                .dnsBindHost("0.0.0.0")
                .dnsPort(53)
                .tcpBindHost("0.0.0.0")
                .tcpPort(443)
                .dohBindHost("0.0.0.0")
                .dohPort(8443)
                .doqBindHost("0.0.0.0")
                .doqPort(853)
                .dotBindHost("0.0.0.0")
                .dotPort(853)
                .doh3BindHost("0.0.0.0")
                .doh3Port(443)
                .dnsAllow(AllowNetworks.everyone())
                .tcpAllow(AllowNetworks.everyone())
                .build();
    }

    /**
     * Exact name wins. Otherwise the wildcard with the longest suffix.
     */
    public Route match(String rawName) {
        String query = RelayNames.normalizeQuery(rawName);
        if (query == null) {
            return null;
        }
        Route exact = null;
        Route wildcard = null;
        int wildcardLabels = -1;
        for (Route route : routes) {
            if (!RelayNames.matches(route.pattern, query)) {
                continue;
            }
            if (!route.wildcard) {
                exact = route;
                break;
            }
            int labels = route.pattern.split("\\.", -1).length;
            if (labels > wildcardLabels) {
                wildcard = route;
                wildcardLabels = labels;
            }
        }
        return exact != null ? exact : wildcard;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean dnsAllows(InetAddress address) {
        return dnsAllow.allows(address);
    }

    public boolean tcpAllows(InetAddress address) {
        return tcpAllow.allows(address);
    }

    @Getter
    public static final class Route {
        private final String pattern;
        private final boolean wildcard;
        private final RelayRouteMode mode;
        private final String upstreamHost;
        private final Integer upstreamPort;
        private final InetAddress answerAddress;

        public Route(String pattern, RelayRouteMode mode, String upstreamHost, Integer upstreamPort,
                     InetAddress answerAddress) {
            this.pattern = pattern;
            this.wildcard = RelayNames.wildcard(pattern);
            this.mode = mode;
            this.upstreamHost = upstreamHost;
            this.upstreamPort = upstreamPort;
            this.answerAddress = answerAddress;
        }
    }

    public static List<Route> copyRoutes(List<Route> routes) {
        return new ArrayList<>(routes);
    }
}
