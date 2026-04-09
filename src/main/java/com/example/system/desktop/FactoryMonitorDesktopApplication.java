package com.example.system.desktop;

import com.example.system.MonitorApplication;
import com.example.system.config.SingleInstanceSupport;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.concurrent.Worker;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Embeds the Spring Boot dashboard in a JavaFX {@link WebView}: dedicated window. Whether closing the window exits
 * the JVM is controlled by {@link SingleInstanceSupport#isDesktopAllowWindowClose()}; when false, the close
 * request is ignored and an informational dialog explains that admins must use Admin → Shut down application.
 * Press F11 to toggle fullscreen after Esc exits it. Single-instance re-launch focuses this window via
 * {@link InstanceActivationServer}.
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

        String windowTitle = resolveWindowTitle();
        stage.setTitle(windowTitle);
        applyStageIcon(stage);
        WebView webView = new WebView();
        webView.setStyle("-fx-background-color: transparent;");
        webView.getEngine().loadContent(buildSplashHtml(windowTitle));
        Scene scene = new Scene(new StackPane(webView), 1280, 800);
        scene.setFill(splashSceneFill());
        stage.setScene(scene);
        stage.setOnCloseRequest(
                e -> {
                    e.consume();
                    if (SingleInstanceSupport.isDesktopAllowWindowClose()) {
                        shutdown(stage);
                    } else {
                        showCloseBlockedHint();
                    }
                });
        installDesktopChrome(scene, stage, webView, httpPort);
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

    private static LinearGradient splashSceneFill() {
        return new LinearGradient(
                0,
                0,
                1,
                1,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0f1419")),
                new Stop(0.55, Color.web("#1a2744")),
                new Stop(1, Color.web("#0d2137")));
    }

    private static String buildSplashHtml(String appTitle) {
        String logoImg = "";
        byte[] logoBytes = readClasspathLogoPngBytes();
        if (logoBytes != null && logoBytes.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(logoBytes);
            logoImg =
                    "<img class=\"logo\" src=\"data:image/png;base64,"
                            + b64
                            + "\" alt=\"\" width=\"96\" height=\"96\"/>";
        }
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                + "<style>"
                + "*{box-sizing:border-box}"
                + "html,body{height:100%;margin:0}"
                + "body{display:flex;flex-direction:column;align-items:center;justify-content:center;"
                + "font-family:system-ui,-apple-system,\"Segoe UI\",sans-serif;"
                + "background:linear-gradient(145deg,#0f1419 0%,#1a2744 50%,#0d2137 100%);"
                + "color:#e8eef7;-webkit-font-smoothing:antialiased}"
                + ".logo{display:block;width:96px;height:96px;margin:0 0 1.5rem;object-fit:contain;"
                + "border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,.35)}"
                + "h1{font-size:1.75rem;font-weight:600;letter-spacing:.02em;margin:0 0 .5rem;text-align:center;"
                + "max-width:90vw;line-height:1.25}"
                + ".sub{font-size:1rem;color:#8fa3bf;margin:0 0 2rem;text-align:center}"
                + ".spinner{width:40px;height:40px;border:3px solid rgba(255,255,255,.15);"
                + "border-top-color:#5b9fd4;border-radius:50%;animation:fxspin .9s linear infinite}"
                + "@keyframes fxspin{to{transform:rotate(360deg)}}"
                + "</style></head><body>"
                + logoImg
                + "<h1>"
                + escapeHtml(appTitle)
                + "</h1>"
                + "<p class=\"sub\">Starting…</p>"
                + "<div class=\"spinner\" role=\"status\" aria-label=\"Loading\"></div>"
                + "</body></html>";
    }

    private static byte[] readClasspathLogoPngBytes() {
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
                    return in.readAllBytes();
                }
            } catch (IOException e) {
                log.debug("Could not read splash logo from classpath: {}", e.getMessage());
            }
        }
        return null;
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

    private void installDesktopChrome(Scene scene, Stage stage, WebView webView, int httpPort) {
        scene.addEventFilter(
                KeyEvent.KEY_PRESSED,
                e -> {
                    if (e.getCode() == KeyCode.F11) {
                        stage.setFullScreen(!stage.isFullScreen());
                        e.consume();
                    }
                });
        log.info(
                "Desktop: press F11 to toggle fullscreen (Esc exits fullscreen). "
                        + "Event log CSV/PDF uses a save dialog when downloaded from this app.");
        webView
                .getEngine()
                .getLoadWorker()
                .stateProperty()
                .addListener(
                        (obs, oldState, newState) -> {
                            if (newState == Worker.State.SUCCEEDED) {
                                injectExportBridge(webView, stage, httpPort);
                            }
                        });
    }

    private static void injectExportBridge(WebView webView, Stage stage, int httpPort) {
        try {
            JSObject win = (JSObject) webView.getEngine().executeScript("window");
            if (win == null) {
                return;
            }
            win.setMember("factoryMonitorDesktop", new DesktopEventLogExportBridge(stage, httpPort));
        } catch (Exception e) {
            log.debug("Could not inject desktop export bridge: {}", e.toString());
        }
    }

    private static void applyDesktopFullscreen(Stage stage) {
        if (!SingleInstanceSupport.isDesktopFullscreen()) {
            return;
        }
        stage.setFullScreen(true);
        log.info(
                "Desktop window: exclusive fullscreen enabled on startup (Esc exits; F11 toggles). "
                        + "Set system.desktop-fullscreen=false for a normal window.");
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

    private static void showCloseBlockedHint() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Production Calling System");
        alert.setHeaderText("Window close disabled");
        alert.setContentText(
                "This session stays running. To stop the application, sign in as an admin and use "
                        + "Admin → Shut down application.");
        alert.showAndWait();
    }
}
