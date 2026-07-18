# Proxy (JAR + WAR, Spring Boot 2 / 3)

HTTP / HTTPS / SOCKS4 / SOCKS5 proxy with SQLite accounts and admin web UI.

Package: `io.github.connellite.proxy`.

## Build profiles

Manifold `#if SPRING_BOOT_2/3`, default **spring-2**. Each `package` produces **JAR** + **WAR**.

```bash
mvn -Pspring-2 clean package   # proxy-1.0.0.jar / .war
mvn -Pspring-3 clean package   # proxy-1.0.0-spring-3.jar / .war
```

## Run (embedded)

```bash
java -jar target/proxy-1.0.0.jar
```

- Admin UI: http://localhost:8080/ (`admin` / `admin`)
- HTTP `:3128` · HTTPS TLS-to-proxy `:3129` (off by default) · SOCKS4/5 `:1080`

## Protocols / ZeroOmega

Full detail: [docs/PROTOCOLS.md](docs/PROTOCOLS.md)

| Profile | Protocol | Port | Notes |
|---------|----------|------|--------|
| HTTP | HTTP | 3128 | HTTPS websites via `CONNECT` |
| HTTPS | HTTPS | 3129 | TLS to proxy; enable in Settings (self-signed) |
| SOCKS5 | SOCKS5 | 1080 | user/password when auth on |
| SOCKS4 | SOCKS4 | 1080 | only when auth is **off** |

## Active connections

Open inbound client TCP channels after accept (not “users”). Released on channel close; reset on listener restart. See docs.
