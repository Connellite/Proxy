package io.github.connellite.proxy.dns.relay;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Parsed networks for one allowlist. Empty means every address is allowed. */
public final class RelayNetworks {

    private static final RelayNetworks EVERYONE = new RelayNetworks(List.of());

    private final List<IPAddress> networks;

    private RelayNetworks(List<IPAddress> networks) {
        this.networks = networks;
    }

    public static RelayNetworks everyone() {
        return EVERYONE;
    }

    public static RelayNetworks parseAll(List<String> cidrs) {
        if (cidrs == null || cidrs.isEmpty()) {
            return EVERYONE;
        }
        List<IPAddress> networks = new ArrayList<>();
        for (String cidr : cidrs) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            networks.add(network(cidr));
        }
        return networks.isEmpty() ? EVERYONE : new RelayNetworks(List.copyOf(networks));
    }

    /** Canonical CIDR. A single address becomes {@code /32} or {@code /128}. */
    public static String canonical(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Network is required");
        }
        IPAddressString parsed = new IPAddressString(raw.trim());
        IPAddress address = parsed.getAddress();
        if (address == null || !parsed.isValid() || address.isPrefixed() && address.getNetworkPrefixLength() == null) {
            throw new IllegalArgumentException("Network must be an IP or CIDR");
        }
        IPAddress block = address.isPrefixed() ? address.toPrefixBlock() : address.assignPrefixForSingleBlock().toPrefixBlock();
        String text = block.toCanonicalString();
        if (text.length() > 64) {
            throw new IllegalArgumentException("Network must be an IP or CIDR");
        }
        return text;
    }

    public boolean allows(InetAddress address) {
        if (networks.isEmpty()) {
            return true;
        }
        if (address == null) {
            return false;
        }
        IPAddress ip = new IPAddressString(address.getHostAddress()).getAddress();
        if (ip == null) {
            return false;
        }
        for (IPAddress network : networks) {
            if (network.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    private static IPAddress network(String cidr) {
        IPAddress address = new IPAddressString(cidr).getAddress();
        if (address == null) {
            throw new IllegalArgumentException("Network must be an IP or CIDR");
        }
        return address.isPrefixed() ? address.toPrefixBlock() : address.assignPrefixForSingleBlock().toPrefixBlock();
    }
}
