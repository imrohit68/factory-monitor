package com.example.system.desktop;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exposed to the embedded WebView as {@code window.factoryMonitorDesktop} so event log CSV/PDF export can use a
 * native save dialog (JavaFX WebView does not handle blob downloads reliably).
 */
public class DesktopEventLogExportBridge {

    private static final Pattern FILENAME_STAR = Pattern.compile("filename\\*=UTF-8''([^;\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME_QUOTED = Pattern.compile("filename=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final Stage owner;
    private final int httpPort;

    public DesktopEventLogExportBridge(Stage owner, int httpPort) {
        this.owner = owner;
        this.httpPort = httpPort;
    }

    /** Called from JavaScript; marshals to the JavaFX application thread. */
    public void saveEventLogExport(String kind, String start, String end) {
        Platform.runLater(() -> doSave(kind, start, end));
    }

    private void doSave(String kind, String start, String end) {
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            alertError("Please choose both dates.");
            return;
        }
        String path = "pdf".equalsIgnoreCase(kind != null ? kind.trim() : "") ? "/report/export.pdf" : "/report/export.csv";
        String ext = path.endsWith(".pdf") ? ".pdf" : ".csv";
        String defaultName = "event-log-" + start + "-to-" + end + ext;
        try {
            String q =
                    "start="
                            + URLEncoder.encode(start, StandardCharsets.UTF_8)
                            + "&end="
                            + URLEncoder.encode(end, StandardCharsets.UTF_8);
            URI uri = URI.create("http://127.0.0.1:" + httpPort + path + "?" + q);

            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(120_000);

            CookieHandler ch = CookieHandler.getDefault();
            if (ch != null) {
                Map<String, List<String>> req = new HashMap<>();
                Map<String, List<String>> cookieHeaders = ch.get(uri, req);
                List<String> cookies = cookieHeaders.get("Cookie");
                if (cookies != null && !cookies.isEmpty()) {
                    conn.setRequestProperty("Cookie", String.join("; ", cookies));
                }
            }

            int code = conn.getResponseCode();
            URI cookieUri = conn.getURL().toURI();
            if (ch != null) {
                ch.put(cookieUri, conn.getHeaderFields());
            }

            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                alertError("You are not signed in or your session expired. Sign in again and retry the export.");
                return;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                String err = readTextStream(conn.getErrorStream());
                alertError(err != null && !err.isBlank() ? err.trim() : ("Export failed (HTTP " + code + ")."));
                return;
            }

            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                body = readAllBytes(in);
            }
            String filename = parseFilename(conn.getHeaderField("Content-Disposition"), defaultName);

            FileChooser fc = new FileChooser();
            fc.setTitle("Save event log");
            fc.setInitialFileName(filename);
            if (filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            } else {
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            }
            File dest = fc.showSaveDialog(owner);
            if (dest == null) {
                return;
            }
            Files.write(dest.toPath(), body);
        } catch (Exception e) {
            alertError(e.getMessage() != null ? e.getMessage() : "Export failed.");
        }
    }

    private static String readTextStream(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        try (in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            in.transferTo(bos);
            return bos.toByteArray();
        }
    }

    private void alertError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Production Calling System");
        a.setHeaderText("Export");
        a.setContentText(msg);
        a.showAndWait();
    }

    static String parseFilename(String contentDisposition, String fallback) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return fallback;
        }
        Matcher m = FILENAME_STAR.matcher(contentDisposition);
        if (m.find()) {
            try {
                return java.net.URLDecoder.decode(m.group(1).trim(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return m.group(1).trim();
            }
        }
        m = FILENAME_QUOTED.matcher(contentDisposition);
        if (m.find()) {
            return m.group(1);
        }
        int idx = contentDisposition.toLowerCase(Locale.ROOT).indexOf("filename=");
        if (idx >= 0) {
            String rest = contentDisposition.substring(idx + "filename=".length()).trim();
            rest = rest.replaceFirst("^[\"']|[\"']$", "");
            int semi = rest.indexOf(';');
            if (semi >= 0) {
                rest = rest.substring(0, semi).trim();
            }
            if (!rest.isBlank()) {
                return rest;
            }
        }
        return fallback;
    }
}
