package io.github.connellite.proxy.dns.relay;

import io.github.connellite.proxy.model.RelayRouteMode;
import org.xbill.DNS.Type;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * What to answer for one question against the current snapshot.
 */
public enum RelayAnswers {
    FORWARD,
    ANSWER,
    NODATA,
    SERVFAIL;

    public static RelayAnswers decide(RelaySnapshot.Route route, int type) {
        if (route == null) {
            return FORWARD;
        }
        InetAddress address = route.getMode() == RelayRouteMode.RELAY ? null : route.getAnswerAddress();
        return decide(route, type, address);
    }

    public static RelayAnswers decide(RelaySnapshot.Route route, int type, InetAddress relayAddress) {
        if (route == null) {
            return FORWARD;
        }
        InetAddress address = route.getMode() == RelayRouteMode.RELAY ? relayAddress : route.getAnswerAddress();
        if (address == null) {
            return SERVFAIL;
        }
        boolean v4 = address instanceof Inet4Address;
        if (type == Type.A && v4 || type == Type.AAAA && !v4) {
            return ANSWER;
        }
        return NODATA;
    }
}
