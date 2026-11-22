package io.github.connellite.proxy.relay;

import io.github.connellite.proxy.model.RelayRouteMode;
import lombok.Getter;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable working copy. DNS and new accepts read this; open connections do not.
 */
public record RelaySnapshot(List<Route> routes,
                            InetAddress advertisedAddress,
                            String resolverHost,
                            int resolverPort,
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
                            RelayNetworks dnsAllow,
                            RelayNetworks tcpAllow) {

    public static final int ANSWER_TTL_SECONDS = 30;
    public static final int MAX_FORWARDS = 32;

    public RelaySnapshot(List<Route> routes, InetAddress advertisedAddress, String resolverHost, int resolverPort,
                         boolean dnsEnabled, String dnsBindHost, int dnsPort,
                         boolean tcpEnabled, String tcpBindHost, int tcpPort,
                         boolean dohEnabled, String dohBindHost, int dohPort,
                         boolean doqEnabled, String doqBindHost, int doqPort,
                         RelayNetworks dnsAllow, RelayNetworks tcpAllow) {
        this.routes = List.copyOf(routes);
        this.advertisedAddress = advertisedAddress;
        this.resolverHost = resolverHost;
        this.resolverPort = resolverPort;
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
        this.dnsAllow = dnsAllow == null ? RelayNetworks.everyone() : dnsAllow;
        this.tcpAllow = tcpAllow == null ? RelayNetworks.everyone() : tcpAllow;
    }

    public static RelaySnapshot defaults() {
        return new RelaySnapshot(List.of(), null, "1.1.1.1", 53,
                false, "0.0.0.0", 53, false, "0.0.0.0", 443,
                false, "0.0.0.0", 8443, false, "0.0.0.0", 853,
                RelayNetworks.everyone(), RelayNetworks.everyone());
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
