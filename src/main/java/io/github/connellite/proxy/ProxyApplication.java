package io.github.connellite.proxy;

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
        SpringApplication app = new SpringApplication(ProxyApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }
}
