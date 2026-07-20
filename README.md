# Proxy (JAR + WAR + Native, Spring Boot 2 / 3)

HTTP / HTTPS / SOCKS4 / SOCKS5 proxy with SQLite accounts and admin web UI.

Package: `io.github.connellite.proxy`.

## Modules

| Module | Artifact | Role |
|--------|----------|------|
| `rpc` | `proxy-rpc` | Shared GWT-RPC DTOs / API |
| `client` | `proxy-client` | GWT admin UI |
| `server` | `proxy` | Spring Boot proxy + admin |

Build from the repository root (reactor). Profiles live on the parent POM.

## Build profiles

Manifold `#if SPRING_BOOT_2/3`, default **spring-2**. Each JVM `package` produces **JAR** + **WAR** under `server/target/`.
Native is an **additional** GraalVM build on Spring Boot 3 (does not replace the JVM artifacts).

```bash
mvn -Pspring-2 clean package     # server/target/proxy-1.0.0.jar / .war
mvn -Pspring-3 clean package     # server/target/proxy-1.0.0-spring-3.jar / .war
mvn -Pnative native:compile      # server/target/proxy-1.0.0-native (needs GraalVM native-image)
mvn -Pnative spring-boot:build-image   # native OCI image via buildpacks (needs Docker)
```

Native prerequisites: GraalVM / Liberica NIK with `native-image`. On Windows use **x64 Native Tools Command Prompt** (Visual Studio Build Tools).

## Run (embedded)

```bash
java -jar server/target/proxy-1.0.0.jar
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
| SOCKS4 | SOCKS4 | 1080 | only when SOCKS auth is **off** |

## Active connections

Open inbound client TCP channels after accept (not “users”). Released on channel close; reset on listener restart. See docs.
