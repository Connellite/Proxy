package io.github.connellite.proxy.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bind-host choices for proxy listeners: all interfaces, localhost, then up IPv4 NICs.
 */
@UtilityClass
public final class LocalBindAddresses {

    public static final String ALL_INTERFACES = "0.0.0.0";
    public static final String LOCALHOST = "127.0.0.1";

    public static List<String> list() {
        Set<String> addresses = new LinkedHashSet<>();
        addresses.add(ALL_INTERFACES);
        addresses.add(LOCALHOST);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return new ArrayList<>(addresses);
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : nif.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            // Fall back to the two fixed options.
        }
        return new ArrayList<>(addresses);
    }

    /** Ensures a currently saved host remains selectable even if the NIC is gone. */
    public static List<String> optionsIncluding(String... selected) {
        Set<String> addresses = new LinkedHashSet<>(list());
        if (selected != null) {
            for (String value : selected) {
                String trimmed = StringUtils.trimToNull(value);
                if (trimmed != null) {
                    addresses.add(trimmed);
                }
            }
        }
        return List.copyOf(addresses);
    }
}
