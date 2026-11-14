package io.github.connellite.proxy.config;

import lombok.Getter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Port and context path the embedded web server actually bound (covers {@code server.port=0} and
 * command-line overrides), as recommended in Spring Boot's "Discover the HTTP Port at Runtime".
 * <p>
 * The event is not published for a WAR on an external Tomcat, so {@link #getPort()} stays {@code 0} there.
 */
@Getter
@Component
public class ServerPort {

   // A separate Actuator server ({@code management.server.port}) publishes the same event afterwards
    private static final String MANAGEMENT_NAMESPACE = "management";

    // Bound port
    private volatile int port;
    // Servlet context path:
    private volatile String contextPath = "";

    @EventListener
    public void onApplicationEvent(WebServerInitializedEvent event) {
        WebServerApplicationContext webContext = event.getApplicationContext();
        if (MANAGEMENT_NAMESPACE.equals(webContext.getServerNamespace())) {
            return;
        }
        if (webContext instanceof WebApplicationContext servletContext && servletContext.getServletContext() != null) {
            contextPath = servletContext.getServletContext().getContextPath();
        }
        port = event.getWebServer().getPort();
    }

}
