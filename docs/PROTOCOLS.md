# Protocols & ZeroOmega

## Active connections (dashboard)

Counted as **open inbound TCP channels** from clients after they are accepted:

| Moment | Counted? |
|--------|----------|
| TCP connect, no data yet | no |
| HTTP: passed auth (or auth off) on first proxy request | yes (+1) |
| SOCKS5: after NO_AUTH / successful password | yes (+1) |
| SOCKS4: CONNECT accepted (only if auth off) | yes (+1) |
| Client disconnect / idle timeout / listener restart | −1 |

One browser tab often opens **many** channels (parallel CONNECT). The number is live sockets, not “users”.

Release is tied to Netty `channel.closeFuture()` (once per channel). On listener restart the counter is reset so it cannot stick.

Traffic counters are **lifetime totals in SQLite** (`app_settings.bytes_*_total` + per-user columns), flushed every few seconds. The dashboard shows DB + not-yet-flushed pending.

---

## What “HTTPS sites” vs ZeroOmega “HTTPS” mean

### Surfing `https://…` through an HTTP proxy

Already works on the **HTTP** listener (`3128`): the browser sends `CONNECT host:443`, the proxy opens a TCP tunnel.  
In ZeroOmega use protocol **HTTP**, not HTTPS.

### ZeroOmega protocol **HTTPS**

Means: TLS **to the proxy itself**, then the usual HTTP-proxy protocol inside TLS.  
Enable **HTTPS proxy** in Settings (default port `3129`, self-signed cert). Browser/OS will warn about the certificate until you trust it.

```
ZeroOmega → HTTPS → host:3129  (TLS) → CONNECT/GET … → target
ZeroOmega → HTTP  → host:3128  (plain) → CONNECT/GET … → target
```

---

## SOCKS4 / SOCKS5

Same port (default `1080`), Netty auto-detects version.

| | SOCKS5 | SOCKS4 / 4a |
|--|--------|-------------|
| ZeroOmega protocol | SOCKS5 | SOCKS4 |
| Auth (user/password) | yes | no (rejected if “require auth” is on) |
| Chrome password auth | often unsupported | n/a |

---

## Quick ZeroOmega profiles

| Profile | Protocol | Port | Notes |
|---------|----------|------|--------|
| Main | HTTP | 3128 | HTTPS websites via CONNECT |
| TLS proxy | HTTPS | 3129 | Enable in Settings; trust cert |
| SOCKS5 | SOCKS5 | 1080 | Prefer for multi-protocol |
| SOCKS4 | SOCKS4 | 1080 | Only with auth **off** |
