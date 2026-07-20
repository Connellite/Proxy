package io.github.connellite.proxy.ui;

import io.github.connellite.proxy.util.AdminServerPortStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows JVM system tray: open the admin UI in the default browser, or quit.
 * Skipped under GraalVM native images — AWT/SystemTray is not reliably supported there.
 */
@Slf4j
@Component
@Order(100)
public class WindowsSystemTray implements ApplicationRunner {

    private static final String ICON_RESOURCE = "/static/icons/tray-icon.png";

    private final ConfigurableApplicationContext context;
    private final Environment environment;
    private final AtomicBoolean installed = new AtomicBoolean(false);

    public WindowsSystemTray(ConfigurableApplicationContext context, Environment environment) {
        this.context = context;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isWindows()) {
            return;
        }
        if (isNativeImage()) {
            log.info("System tray skipped under GraalVM native image (use the JVM build for tray support)");
            return;
        }
        if (!installed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (isHeadless() || !SystemTray.isSupported()) {
                installed.set(false);
                log.info("System tray is not available on this Windows session");
                return;
            }
            install();
        } catch (Throwable ex) {
            // Same as isHeadless(): AWT may throw Error (e.g. NoSuchMethodError), not Exception.
            installed.set(false);
            log.warn("Failed to install Windows system tray icon: {}", ex.toString());
        }
    }

    private void install() throws Exception {
        int port = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, AdminServerPortStore.DEFAULT_PORT));
        String adminUrl = "http://127.0.0.1:" + port + "/admin.html";

        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open admin");
        open.addActionListener(e -> openBrowser(adminUrl));
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> shutdown());
        menu.add(open);
        menu.addSeparator();
        menu.add(exit);

        TrayIcon icon = new TrayIcon(loadIconImage(), "Proxy Admin", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> openBrowser(adminUrl));
        SystemTray.getSystemTray().add(icon);
        log.info("Windows system tray icon ready ({})", adminUrl);
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
            }
        } catch (Exception ex) {
            log.warn("Unable to open admin page {}: {}", url, ex.toString());
        }
    }

    private void shutdown() {
        log.info("Exit requested from system tray");
        try {
            int code = SpringApplication.exit(context, () -> 0);
            System.exit(code);
        } catch (Exception ex) {
            System.exit(0);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isNativeImage() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static boolean isHeadless() {
        try {
            return GraphicsEnvironment.isHeadless();
        } catch (Throwable ex) {
            // Catch Throwable (not Exception): AWT/JNI can throw Error subclasses
            // like NoSuchMethodError under GraalVM native — those would bypass catch (Exception)
            // and abort Spring Boot startup. Treat any AWT probe failure as headless.
            return true;
        }
    }

    private static Image loadIconImage() throws Exception {
        try (InputStream in = WindowsSystemTray.class.getResourceAsStream(ICON_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + ICON_RESOURCE);
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalStateException("Unable to decode " + ICON_RESOURCE);
            }
            return image;
        }
    }
}
