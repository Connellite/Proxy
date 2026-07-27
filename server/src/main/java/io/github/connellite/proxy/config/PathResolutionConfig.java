package io.github.connellite.proxy.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWarDeployment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWarDeployment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class PathResolutionConfig {

    @Bean("projectFolder")
    @ConditionalOnWarDeployment
    public String warPathResolver() {
        return System.getProperty("catalina.base");
    }

    @Bean("projectFolder")
    @ConditionalOnNotWarDeployment
    public String jarPathResolver() {
        return System.getProperty("user.dir");
    }

    /**
     * Resolves the data directory: explicit {@code proxy.data-dir}, else
     * {@code {projectFolder}/data}, else {@code ./data} (WAR without Tomcat / missing base).
     */
    @Bean("dataDir")
    public Path dataDir(ProxyProperties proxyProperties, @Qualifier("projectFolder") String projectFolder) throws Exception {
        Path dir = resolveDataDir(proxyProperties.getDataDir(), projectFolder);
        Files.createDirectories(dir);
        proxyProperties.setDataDir(dir.toString());
        return dir;
    }

    static Path resolveDataDir(String configuredDataDir, String projectFolder) {
        String configured = StringUtils.trimToNull(configuredDataDir);
        if (configured != null) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        if (StringUtils.isNotBlank(projectFolder)) {
            return Path.of(projectFolder, "data").toAbsolutePath().normalize();
        }
        // WAR deployment without Tomcat (no catalina.base), or missing project folder.
        return Path.of("./data").toAbsolutePath().normalize();
    }
}
