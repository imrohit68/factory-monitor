package com.example.system.desktop;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Primary JVM: listens on loopback for {@code ACTIVATE} so a second launch can focus the existing window.
 */
public final class InstanceActivationServer {

    private static final Logger log = LoggerFactory.getLogger(InstanceActivationServer.class);

    private final ServerSocket serverSocket;
    private final Thread thread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Stage stage;

    public InstanceActivationServer(int port, Stage stage) throws IOException {
        this.stage = stage;
        this.serverSocket = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        this.thread = new Thread(this::acceptLoop, "factory-monitor-activation");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    private void acceptLoop() {
        while (running.get()) {
            try (Socket client = serverSocket.accept();
                    BufferedReader in =
                            new BufferedReader(
                                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                String line = in.readLine();
                if ("ACTIVATE".equals(line)) {
                    Platform.runLater(
                            () -> {
                                stage.setIconified(false);
                                stage.show();
                                stage.toFront();
                                stage.requestFocus();
                            });
                }
            } catch (SocketException e) {
                if (running.get()) {
                    log.debug("Activation server socket: {}", e.getMessage());
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.warn("Activation accept: {}", e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running.set(false);
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        thread.interrupt();
    }
}
