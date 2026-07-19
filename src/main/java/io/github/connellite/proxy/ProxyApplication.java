package io.github.connellite.proxy;

import io.github.connellite.proxy.config.AdminServerPortStore;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProxyApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ProxyApplication.class).bannerMode(Banner.Mode.OFF);
    }

    public static void main(String[] args) {
        if (isWindows()) {
            // Required for SystemTray / Desktop.browse on desktop Windows sessions.
            System.setProperty("java.awt.headless", "false");
        }
        SpringApplication app = new SpringApplication(ProxyApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setDefaultProperties(AdminServerPortStore.asServerPortProperties(AdminServerPortStore.readConfiguredPort()));
        app.run(args);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
