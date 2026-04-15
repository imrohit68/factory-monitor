package com.example.system.desktop;

import com.example.system.MonitorApplication;
import com.example.system.config.OperationMode;
import com.example.system.config.SingleInstanceSupport;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.concurrent.Worker;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/**
 * Embeds the Spring Boot dashboard in a JavaFX {@link WebView}: dedicated window. The window close (✕) button does
 * not exit the app; it shows an instruction to shut down from the admin panel. Use Admin → Shut down application
 * to stop the JVM.
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
        OperationMode selectedMode = showModeSelectionDialog(stage);
        if (selectedMode == null) {
            // If the chooser is dismissed, continue with the safe default instead of leaving the desktop shell half-open.
            selectedMode = OperationMode.PRODUCTION;
        }
        SingleInstanceSupport.setOperationMode(selectedMode);
        log.info("Starting application in {} mode", selectedMode.name());

        String windowTitle = resolveWindowTitle();
        stage.setTitle(windowTitle);
        applyStageIcon(stage);
        WebView webView = new WebView();
        webView.setStyle("-fx-background-color: transparent;");
        webView.getEngine().loadContent(buildSplashHtml(windowTitle, selectedMode.getDisplayName()));
        Scene scene = new Scene(new StackPane(webView), 1280, 800);
        scene.setFill(splashSceneFill());
        stage.setScene(scene);
        stage.setOnCloseRequest(
                e -> {
                    e.consume();
                    showCloseInstructionDialog();
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
                            // wv=1: skip browser-only dashboard gate when desktop-mode JVM is opened in this WebView
                            // (external browsers still see the gate at /).
                            String url = "http://127.0.0.1:" + httpPort + "/?wv=1";
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

    private OperationMode showModeSelectionDialog(Stage ownerStage) {
        Properties appProps = loadApplicationPropertiesForDesktop();
        int timeoutSec = modeSelectionTimeoutSeconds(appProps);
        String[] gate = resolveMaintenanceGateCredentials(appProps);
        String gateUser = gate[0];
        String gatePass = gate[1];

        Stage dialog = new Stage(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(ownerStage);
        dialog.setTitle("Select Mode");
        dialog.setResizable(false);
        applyStageIcon(dialog);

        Label title = new Label("Select Mode");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#e8eef7"));

        Label subtitle = new Label("Choose how to run the application");
        subtitle.setFont(Font.font("System", FontWeight.NORMAL, 13));
        subtitle.setTextFill(Color.web("#8fa3bf"));

        AtomicInteger secondsLeft = new AtomicInteger(timeoutSec);
        Label countdownLabel = new Label();
        countdownLabel.setFont(Font.font("System", FontWeight.NORMAL, 13));
        countdownLabel.setTextFill(Color.web("#94a8c4"));
        countdownLabel.setText("Starting Production automatically in " + timeoutSec + "s…");

        VBox productionCard = buildModeCard(
                "Production Mode",
                "Connect to Modbus hardware.\nReads inputs from the physical device.",
                "#10b981",
                "#065f46");
        VBox maintenanceCard = buildModeCard(
                "Maintenance Mode",
                "No hardware required.\nToggle inputs from the dashboard grid.",
                "#8b5cf6",
                "#4c1d95");

        final OperationMode[] selected = {null};
        final Timeline[] timelineRef = new Timeline[1];

        Runnable restartCountdown =
                () -> {
                    secondsLeft.set(timeoutSec);
                    countdownLabel.setText(
                            "Starting Production automatically in " + timeoutSec + "s…");
                    if (timelineRef[0] != null) {
                        timelineRef[0].stop();
                        timelineRef[0].playFromStart();
                    }
                };

        timelineRef[0] =
                new Timeline(
                        new KeyFrame(
                                Duration.seconds(1),
                                e -> {
                                    int left = secondsLeft.decrementAndGet();
                                    if (left > 0) {
                                        countdownLabel.setText(
                                                "Starting Production automatically in " + left + "s…");
                                    } else {
                                        timelineRef[0].stop();
                                        if (selected[0] == null) {
                                            selected[0] = OperationMode.PRODUCTION;
                                            dialog.close();
                                        }
                                    }
                                }));
        timelineRef[0].setCycleCount(Timeline.INDEFINITE);

        productionCard.setOnMouseClicked(
                e -> {
                    timelineRef[0].stop();
                    selected[0] = OperationMode.PRODUCTION;
                    dialog.close();
                });
        maintenanceCard.setOnMouseClicked(
                e -> {
                    timelineRef[0].stop();
                    if (promptMaintenanceGate(dialog, gateUser, gatePass)) {
                        selected[0] = OperationMode.MAINTENANCE;
                        dialog.close();
                    } else {
                        restartCountdown.run();
                    }
                });

        HBox cards = new HBox(24, productionCard, maintenanceCard);
        cards.setAlignment(Pos.CENTER);

        VBox root = new VBox(16, title, subtitle, countdownLabel, cards);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40, 48, 40, 48));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #0f1419, #1a2744, #0d2137);"
                        + "-fx-background-radius: 12; -fx-border-radius: 12;"
                        + "-fx-border-color: rgba(255,255,255,0.08); -fx-border-width: 1;");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        timelineRef[0].play();
        dialog.showAndWait();
        return selected[0];
    }

    private static VBox buildModeCard(String titleText, String bodyText, String accent, String accentDark) {
        Label title = new Label(titleText);
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#f8fbff"));

        Label body = new Label(bodyText);
        body.setWrapText(true);
        body.setTextFill(Color.web("#c7d4e6"));
        body.setFont(Font.font("System", FontWeight.NORMAL, 13));

        Region accentBar = new Region();
        accentBar.setPrefSize(56, 4);
        accentBar.setMaxWidth(56);
        accentBar.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 999;");

        VBox card = new VBox(14, accentBar, title, body);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(22));
        card.setPrefWidth(260);
        card.setMinWidth(260);
        card.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, rgba(255,255,255,0.07), rgba(255,255,255,0.03));"
                        + "-fx-background-radius: 14; -fx-border-radius: 14;"
                        + "-fx-border-color: "
                        + accentDark
                        + "; -fx-border-width: 1.2;"
                        + "-fx-cursor: hand;");
        return card;
    }

    private static String buildSplashHtml(String appTitle, String modeLabel) {
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
                + "<p class=\"sub\">Starting in "
                + escapeHtml(modeLabel)
                + "…</p>"
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

    /**
     * Classpath {@code application.properties} merged with {@code user.dir/config/application.properties}
     * when present (same overlay order Spring Boot uses for packaged installs).
     */
    private static Properties loadApplicationPropertiesForDesktop() {
        Properties combined = new Properties();
        ClassLoader[] loaders = {
            Thread.currentThread().getContextClassLoader(),
            FactoryMonitorDesktopApplication.class.getClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try (InputStream raw = cl.getResourceAsStream("application.properties")) {
                if (raw == null) {
                    continue;
                }
                try (InputStreamReader reader = new InputStreamReader(raw, StandardCharsets.UTF_8)) {
                    combined.load(reader);
                    break;
                }
            } catch (IOException e) {
                log.debug("Could not load classpath application.properties: {}", e.getMessage());
            }
        }
        Path external = externalApplicationPropertiesPath();
        if (external != null) {
            try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                Properties overlay = new Properties();
                overlay.load(reader);
                combined.putAll(overlay);
            } catch (IOException e) {
                log.warn("Could not read {}: {}", external, e.getMessage());
            }
        }
        return combined;
    }

    private static Path externalApplicationPropertiesPath() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null || userDir.isBlank()) {
            return null;
        }
        Path p = Path.of(userDir, "config", "application.properties");
        return Files.isRegularFile(p) ? p : null;
    }

    private static int modeSelectionTimeoutSeconds(Properties p) {
        String raw = p.getProperty("system.desktop.mode-selection-timeout-seconds", "10");
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private static String[] resolveMaintenanceGateCredentials(Properties props) {
        String gateUser = trimOrEmpty(props.getProperty("system.desktop.maintenance-gate-username"));
        String gatePass = trimOrEmpty(props.getProperty("system.desktop.maintenance-gate-password"));
        if (gateUser.isEmpty()) {
            gateUser = props.getProperty("system.security.initial-username", "admin");
        }
        if (gatePass.isEmpty()) {
            gatePass = props.getProperty("system.security.initial-password", "admin@123");
        }
        return new String[] {gateUser, gatePass};
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = (a != null ? a : "").getBytes(StandardCharsets.UTF_8);
        byte[] right = (b != null ? b : "").getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static boolean promptMaintenanceGate(Stage owner, String expectedUser, String expectedPassword) {
        Dialog<ButtonType> gateDialog = new Dialog<>();
        gateDialog.initOwner(owner);
        gateDialog.initModality(Modality.WINDOW_MODAL);
        gateDialog.setTitle("Maintenance access");
        gateDialog.setHeaderText("Enter credentials for Maintenance Mode");
        gateDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        TextField userField = new TextField();
        PasswordField passField = new PasswordField();
        grid.add(new Label("User ID:"), 0, 0);
        grid.add(userField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passField, 1, 1);
        gateDialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = gateDialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return false;
        }
        if (constantTimeEquals(Objects.toString(userField.getText(), ""), expectedUser)
                && constantTimeEquals(Objects.toString(passField.getText(), ""), expectedPassword)) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Maintenance access");
        alert.setHeaderText(null);
        alert.setContentText("Invalid user ID or password.");
        alert.showAndWait();
        return false;
    }

    private static String resolveWindowTitle() {
        Properties p = loadApplicationPropertiesForDesktop();
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

    private static void showCloseInstructionDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Production Calling System");
        alert.setHeaderText(null);
        alert.setContentText(
                "To close the application, please login to the admin panel and choose Shut Down Application.");
        alert.showAndWait();
    }
}
