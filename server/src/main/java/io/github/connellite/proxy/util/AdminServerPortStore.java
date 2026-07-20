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
     * Early boot: {@code ${proxy.data-dir:./data}/proxy.db} → {@code admin_server_port}.
     */
    public static int readConfiguredPort() {
        Path db = Path.of(System.getProperty("proxy.data-dir", "./data"), "proxy.db");
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

    public static Properties asServerPortProperties(int port) {
        Properties props = new Properties();
        props.setProperty("server.port", Integer.toString(port));
        return props;
    }

    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }
}
