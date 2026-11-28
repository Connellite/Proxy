package io.github.connellite.proxy.proxy.outbound;

import io.github.connellite.proxy.dto.AppSettings;
import io.netty.channel.ChannelPipeline;

/**
 * DPI tweaks for sockets this proxy opens to an upstream, read once per connection.
 */
record OutboundTweaks(
        boolean clientHelloSplit,
        ClientHelloSplitMode splitMode,
        int splitPosition,
        boolean httpHeaderMangle
) {

    static OutboundTweaks from(AppSettings settings) {
        return new OutboundTweaks(
                settings.isClientHelloSplitEnabled(),
                ClientHelloSplitMode.fromWire(settings.getClientHelloSplitMode()),
                settings.getClientHelloSplitPosition(),
                settings.isHttpHeaderMangleEnabled());
    }

    /**
     * Puts the tweak handlers at the socket end of {@code pipeline} and marks the channel for
     * {@link HttpHeaderMangleEncoder#forChannel}.
     *
     * @param upstreamTls {@code true} for an HTTPS upstream: the first handshake record on this socket is the one
     *                    to cut and HTTP inside it is encrypted; otherwise the upstream handshake calls
     *                    {@link ClientHelloSplitHandler#arm}
     */
    void install(ChannelPipeline pipeline, boolean upstreamTls) {
        if (httpHeaderMangle && !upstreamTls) {
            HttpHeaderMangleEncoder.enable(pipeline.channel());
        }
        if (clientHelloSplit) {
            pipeline.addFirst(new ClientHelloSplitHandler(splitMode, splitPosition, upstreamTls));
        }
    }
}
