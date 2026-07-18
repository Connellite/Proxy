# Proxy (JAR + WAR, Spring Boot 2 / 3)

HTTP and SOCKS5 proxy with SQLite accounts and admin web UI.

## Build profiles

Same layout as a dual Spring Boot project: Manifold `#if SPRING_BOOT_2/3`, default **spring-2**, optional **spring-3**. Each `package` produces **JAR** (Spring Boot repackage) and **WAR**.

```bash
# Spring Boot 2.7 (default) → proxy-1.0.0.jar + proxy-1.0.0.war
mvn -Pspring-2 clean package

# Spring Boot 3.5 → proxy-1.0.0-spring-3.jar + proxy-1.0.0-spring-3.war
mvn -Pspring-3 clean package
```

## Run (embedded)

```bash
java -jar target/proxy-1.0.0.jar
# or
java -jar target/proxy-1.0.0-spring-3.jar
```

- Admin UI: http://localhost:8080/ (`admin` / `admin`)
- HTTP proxy: `:3128`
- SOCKS5: `:1080`

## Tomcat

Deploy the `.war`. Listeners still bind the configured HTTP/SOCKS ports when the webapp starts.

SQLite: `./data/proxy.db` (process working directory). Override:

```yaml
proxy:
  data-dir: /var/lib/proxy
```

## ZeroOmega

| Profile | Protocol | Port | Auth |
|---------|----------|------|------|
| HTTP | HTTP | 3128 | proxy user / password |
| SOCKS5 | SOCKS5 | 1080 | proxy user / password (Firefox; Chrome often lacks SOCKS auth) |
