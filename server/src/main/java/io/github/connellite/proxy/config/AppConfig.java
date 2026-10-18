package io.github.connellite.proxy.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.DateTimeException;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class AppConfig {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public ZoneId appZoneId(ProxyProperties proxyProperties) {
        String id = StringUtils.trimToNull(proxyProperties.getTimezone());
        if (id == null) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(id);
        } catch (DateTimeException ex) {
            throw new IllegalStateException("Invalid proxy.timezone: " + id, ex);
        }
    }
}
