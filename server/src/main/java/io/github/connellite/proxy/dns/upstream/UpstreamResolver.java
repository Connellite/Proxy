package io.github.connellite.proxy.dns.upstream;

import lombok.experimental.UtilityClass;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Resolves a relay upstream through the configured resolver, never the system resolver.
 */
@UtilityClass
public final class UpstreamResolver {

    public static CompletionStage<InetAddress> resolve(String host, UpstreamDnsEndpoint endpoint, int timeoutMs) {
        InetAddress literal = literal(host);
        if (literal != null) {
            return CompletableFuture.completedFuture(literal);
        }
        try {
            Resolver resolver = create(endpoint, timeoutMs);
            Name name = Name.fromString(host.endsWith(".") ? host : host + ".");
            return query(resolver, name, Type.A).thenCompose(address -> {
                if (address != null) {
                    return CompletableFuture.completedFuture(address);
                }
                return query(resolver, name, Type.AAAA);
            });
        } catch (Exception ex) {
            CompletableFuture<InetAddress> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    public static Resolver create(UpstreamDnsEndpoint endpoint, int timeoutMs) throws Exception {
        UpstreamDnsEndpoint target = endpoint == null ? UpstreamDnsEndpoint.DEFAULT : endpoint;
        Duration timeout = Duration.ofMillis(Math.max(1, timeoutMs));
        Resolver resolver = switch (target.kind()) {
            case PLAIN, TCP -> {
                SimpleResolver plain = new SimpleResolver(target.host());
                plain.setPort(target.port());
                plain.setTCP(target.kind() == UpstreamDnsEndpoint.Kind.TCP);
                yield plain;
            }
            case DOH -> {
                DohResolver doh = new DohResolver(target.uri());
                doh.setUsePost(true);
                yield doh;
            }
            case DOT -> new DotResolver(target.host(), target.port());
            case DOH3 -> new Doh3Resolver(target.host(), target.port(), target.uri());
            case DOQ -> new DoqResolver(target.host(), target.port());
        };
        resolver.setTimeout(timeout);
        return resolver;
    }

    private static CompletionStage<InetAddress> query(Resolver resolver, Name name, int type) {
        Message query = Message.newQuery(Record.newRecord(name, type, DClass.IN));
        return resolver.sendAsync(query).thenApply(message -> first(message, type));
    }

    private static InetAddress first(Message message, int type) {
        for (Record record : message.getSection(Section.ANSWER)) {
            if (type == Type.A && record instanceof ARecord a) {
                return a.getAddress();
            }
            if (type == Type.AAAA && record instanceof AAAARecord aaaa) {
                return aaaa.getAddress();
            }
        }
        return null;
    }

    private static InetAddress literal(String host) {
        if (host == null || host.indexOf(' ') >= 0) {
            return null;
        }
        boolean v4 = host.chars().filter(c -> c == '.').count() == 3 && host.indexOf(':') < 0;
        boolean v6 = host.indexOf(':') >= 0;
        if (!v4 && !v6) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.getHostAddress().equals(host) || v6) {
                return address;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
