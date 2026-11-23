package io.github.connellite.proxy.dns.upstream;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ScheduledFuture;
import org.xbill.DNS.EDNSOption;
import org.xbill.DNS.Message;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.WireParseException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shared stubs for outbound resolvers that only implement {@link #send(Message, CompletableFuture)}.
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

    @Override
    public final CompletionStage<Message> sendAsync(Message query, Executor executor) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        long timeoutMs = Math.max(1, timeout.toMillis());
        ScheduledFuture<?> timer = group().schedule(() -> future.completeExceptionally(
                new TimeoutException(protocol() + " timed out after " + timeoutMs + " ms")),
                timeoutMs, TimeUnit.MILLISECONDS);
        future.whenComplete((response, error) -> timer.cancel(false));
        executor.execute(() -> {
            try {
                send(query, future);
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    protected abstract String protocol();

    /**
     * Sends {@code query}; must complete {@code future}. The timeout is armed by {@link #sendAsync}, and
     * implementations release their channels in {@code future.whenComplete}.
     */
    protected abstract void send(Message query, CompletableFuture<Message> future) throws Exception;

    protected final long timeoutMs() {
        return Math.max(1, timeout.toMillis());
    }

    protected static EventLoopGroup group() {
        return EventLoopHolder.GROUP;
    }

    /**
     * Parses {@code wire}, checks it answers {@code query} and restores the query ID.
     *
     * <p>https://github.com/AdguardTeam/dnsproxy/blob/v0.86.0/upstream/upstream.go#L470-L489
     */
    protected static Message response(Message query, byte[] wire, int expectedId) throws IOException {
        Message response = new Message(wire);
        if (response.getHeader().getID() != expectedId) {
            throw new WireParseException("unexpected id in response: " + response.getHeader().getID());
        }
        Record question = query.getQuestion();
        Record answered = response.getQuestion();
        if (question != null) {
            if (answered == null
                    || answered.getType() != question.getType()
                    || !answered.getName().equals(question.getName())) {
                throw new WireParseException("bad question section in response");
            }
        }
        response.getHeader().setID(query.getHeader().getID());
        return response;
    }

    /**
     * Copy of {@code query} with message ID 0 (RFC 9250 §4.2.1, RFC 8484 §4.1).
     */
    protected static Message withZeroId(Message query) {
        Message copy = query.clone();
        copy.getHeader().setID(0);
        return copy;
    }

    private static final class EventLoopHolder {
        private static final EventLoopGroup GROUP = new MultiThreadIoEventLoopGroup(0,
                new DefaultThreadFactory("dns-upstream", true), NioIoHandler.newFactory());
    }
}
