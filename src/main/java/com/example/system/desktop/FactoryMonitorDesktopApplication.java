package com.example.system.desktop;

import com.example.system.MonitorApplication;
import com.example.system.config.SingleInstanceSupport;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Embeds the Spring Boot dashboard in a JavaFX {@link WebView}: dedicated window. Whether closing the window exits
 * the JVM is controlled by {@link SingleInstanceSupport#isDesktopAllowWindowClose()}; when false, use Admin → Shut
 * down application. Single-instance re-launch focuses this window via {@link InstanceActivationServer}.
 */
public class FactoryMonitorDesktopApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(FactoryMonitorDesktopApplication.class);

    private InstanceActivationServer activationServer;
    private volatile ConfigurableApplicationContext springContext;

    @Override
    public void start(Stage stage) {
        String[] args = getParameters().getRaw().toArray(String[]::new);
        int httpPort = SingleInstanceSupport.getConfiguredPort();
        int activationPort = SingleInstanceSupport.getActivationPort();

        stage.setTitle(resolveWindowTitle());
        applyStageIcon(stage);
        WebView webView = new WebView();
        webView.getEngine().loadContent(wrapHtml("Starting…"));
        stage.setScene(new Scene(new StackPane(webView), 1280, 800));
        stage.setOnCloseRequest(
                e -> {
                    e.consume();
                    if (SingleInstanceSupport.isDesktopAllowWindowClose()) {
                        shutdown(stage);
                    } else {
                        showCloseBlockedInfo();
                    }
                });
        applyDesktopFullscreen(stage);
        stage.show();

        try {
            activationServer = new InstanceActivationServer(activationPort, stage);
            activationServer.start();
        } catch (IOException e) {
            log.error("Could not start activation server on {}: {}", activationPort, e.getMessage());
            showError("Could not bind activation port " + activationPort + ". Another instance may be starting.");
            shutdown(stage);
            return;
        }

        Thread springThread =
                new Thread(
                        () -> {
                            try {
                                springContext = MonitorApplication.runSpringApplication(args);
                            } catch (Throwable t) {
                                log.error("Spring Boot failed to start", t);
                                Platform.runLater(() -> showError(t.getMessage()));
                            }
                        },
                        "spring-boot");
        springThread.setDaemon(false);
        springThread.start();

        Thread waitUi =
                new Thread(
                        () -> {
                            if (!waitForLocalPort(httpPort, TimeUnit.MINUTES.toMillis(2))) {
                                Platform.runLater(
                                        () -> showError("Timed out waiting for the server on port " + httpPort));
                                return;
                            }
                            String url = "http://127.0.0.1:" + httpPort + "/";
                            Platform.runLater(() -> webView.getEngine().load(url));
                        },
                        "fx-http-wait");
        waitUi.setDaemon(true);
        waitUi.start();
    }

    private static String wrapHtml(String message) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<style>body{font-family:system-ui,sans-serif;padding:2rem;background:#f5f5f5;color:#333}</style>"
                + "</head><body><p>"
                + escapeHtml(message)
                + "</p></body></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String resolveWindowTitle() {
        Properties p = new Properties();
        try (InputStream in =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            log.debug("Could not load application.properties for title: {}", e.getMessage());
        }
        String name = p.getProperty("spring.application.name", "production-calling-system");
        return humanizeAppName(name);
    }

    private static void applyStageIcon(Stage stage) {
        Image fromFile = tryLoadPngBesideLauncher();
        if (fromFile != null && !fromFile.isError()) {
            stage.getIcons().add(fromFile);
            return;
        }
        Image fromClasspath = tryLoadPngFromClasspath();
        if (fromClasspath != null && !fromClasspath.isError()) {
            stage.getIcons().add(fromClasspath);
        }
    }

    /**
     * jpackage install: {@code user.dir} is usually the folder containing {@code ProductionCallingSystem.exe};
     * we copy {@code app-logo.png} there in the Windows build script.
     */
    private static Image tryLoadPngBesideLauncher() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null || userDir.isBlank()) {
            return null;
        }
        Path png = Path.of(userDir, "app-logo.png");
        if (!Files.isRegularFile(png)) {
            return null;
        }
        try {
            return new Image(png.toUri().toString());
        } catch (Exception e) {
            log.debug("Could not load window icon from {}: {}", png, e.getMessage());
            return null;
        }
    }

    private static Image tryLoadPngFromClasspath() {
        ClassLoader[] loaders = {
            Thread.currentThread().getContextClassLoader(),
            FactoryMonitorDesktopApplication.class.getClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try (InputStream in = cl.getResourceAsStream("static/images/app-logo.png")) {
                if (in != null) {
                    byte[] png = in.readAllBytes();
                    return new Image(new ByteArrayInputStream(png));
                }
            } catch (IOException e) {
                log.debug("Could not load window icon from classpath: {}", e.getMessage());
            }
        }
        return null;
    }

    private static void applyDesktopFullscreen(Stage stage) {
        if (!SingleInstanceSupport.isDesktopFullscreen()) {
            return;
        }
        stage.setFullScreen(true);
        log.info("Desktop window: exclusive fullscreen enabled (Esc exits fullscreen; set system.desktop-fullscreen=false for a normal window).");
    }

    static String humanizeAppName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Production Calling System";
        }
        String s = raw.trim().replace('-', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : s.split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase());
            }
        }
        return out.length() > 0 ? out.toString() : "Production Calling System";
    }

    private static boolean waitForLocalPort(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(
                        new java.net.InetSocketAddress(
                                java.net.InetAddress.getLoopbackAddress(), port),
                        300);
                return true;
            } catch (IOException e) {
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private void shutdown(Stage stage) {
        if (activationServer != null) {
            activationServer.stop();
        }
        ConfigurableApplicationContext ctx = springContext;
        if (ctx != null && ctx.isActive()) {
            int code = SpringApplication.exit(ctx, () -> 0);
            log.info("Spring context closed (code {}).", code);
        }
        stage.close();
        Platform.exit();
        System.exit(0);
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Production Calling System");
        alert.setHeaderText("Startup error");
        alert.setContentText(message != null ? message : "Unknown error");
        alert.showAndWait();
    }

    private static void showCloseBlockedInfo() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Production Calling System");
        alert.setHeaderText("Window close disabled");
        alert.setContentText(
                "This kiosk session stays running. To stop the application, sign in as an admin and use "
                        + "Admin → Shut down application.");
        alert.showAndWait();
    }
}
