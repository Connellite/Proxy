package io.github.connellite.proxy.util;

import io.github.connellite.proxy.model.ConfigEntry;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

/**
 * Reads the admin UI port from SQLite before Spring Boot binds Tomcat.
 * The value is edited in Settings and stored as {@link ConfigEntry#ADMIN_SERVER_PORT}.
 */
@UtilityClass
public final class AdminServerPortStore {

    public static final int DEFAULT_PORT = 8080;


    /**
     * Early boot: resolved data dir {@code /proxy.db} → {@code admin_server_port}.
     * Order: {@code -Dproxy.data-dir}, else {@code catalina.base/data}, else {@code user.dir/data}, else {@code ./data}.
     */
    public static int readConfiguredPort() {
        Path db = resolveDataDir().resolve("proxy.db");
        if (!Files.isRegularFile(db)) {
            return DEFAULT_PORT;
        }
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
                 PreparedStatement statement = connection.prepareStatement("select value from config where key = ?")) {
                statement.setString(1, ConfigEntry.ADMIN_SERVER_PORT);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return DEFAULT_PORT;
                    }
                    int port = Integer.parseInt(rs.getString(1).trim());
                    return isValidPort(port) ? port : DEFAULT_PORT;
                }
            }
        } catch (Exception ex) {
            return DEFAULT_PORT;
        }
    }

    static Path resolveDataDir() {
        String configured = System.getProperty("proxy.data-dir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim()).toAbsolutePath().normalize();
        }
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null && !catalinaBase.isBlank()) {
            return Path.of(catalinaBase, "data").toAbsolutePath().normalize();
        }
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            return Path.of(userDir, "data").toAbsolutePath().normalize();
        }
        return Path.of("./data").toAbsolutePath().normalize();
    }

    public static Properties asServerPortProperties(int port) {
        Properties props = new Properties();
        props.setProperty("server.port", Integer.toString(port));
        return props;
    }

    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }
}
