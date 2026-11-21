package io.github.connellite.proxy.relay;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Resolves a relay upstream through the configured resolver, never the system resolver. */
public final class UpstreamResolver {

    private UpstreamResolver() {
    }

    public static CompletionStage<InetAddress> resolve(String host, String resolverHost, int resolverPort, int timeoutMs) {
        InetAddress literal = literal(host);
        if (literal != null) {
            return CompletableFuture.completedFuture(literal);
        }
        try {
            SimpleResolver resolver = resolver(resolverHost, resolverPort, timeoutMs);
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

    private static CompletionStage<InetAddress> query(SimpleResolver resolver, Name name, int type) {
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

    public static SimpleResolver resolver(String host, int port, int timeoutMs) throws Exception {
        SimpleResolver resolver = new SimpleResolver(host);
        resolver.setPort(port);
        resolver.setTimeout(Duration.ofMillis(Math.max(1, timeoutMs)));
        return resolver;
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
