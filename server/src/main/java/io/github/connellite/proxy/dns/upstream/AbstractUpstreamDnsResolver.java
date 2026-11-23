package io.github.connellite.proxy.dns.upstream;

import org.xbill.DNS.EDNSOption;
import org.xbill.DNS.Message;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.TSIG;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Shared stubs for outbound resolvers that only implement {@link #sendAsync(Message, Executor)}.
 */
abstract class AbstractUpstreamDnsResolver implements Resolver {

    private Duration timeout = Duration.ofSeconds(5);

    @Override
    public void setPort(int port) {
    }

    @Override
    public void setTCP(boolean flag) {
    }

    @Override
    public void setIgnoreTruncation(boolean flag) {
    }

    @Override
    public void setEDNS(int version, int payloadSize, int flags, List<EDNSOption> options) {
    }

    @Override
    public void setTSIGKey(TSIG key) {
    }

    @Override
    public void setTimeout(Duration timeout) {
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofMillis(1)
                : timeout;
    }

    @Override
    public Duration getTimeout() {
        return timeout;
    }

    protected final CompletableFuture<Message> failed(Throwable error) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
