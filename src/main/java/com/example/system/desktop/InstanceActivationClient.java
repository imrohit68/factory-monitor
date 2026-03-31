package com.example.system.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Second JVM: notifies the primary desktop instance to show and focus its window.
 */
public final class InstanceActivationClient {

    private static final Logger log = LoggerFactory.getLogger(InstanceActivationClient.class);

    private InstanceActivationClient() {}

    public static boolean tryActivate(int activationPort) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), activationPort), 2_000);
            OutputStream out = s.getOutputStream();
            out.write("ACTIVATE\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            log.debug("Activation connect failed on port {}: {}", activationPort, e.getMessage());
            return false;
        }
    }
}
