package com.example.system.config;

import com.example.system.desktop.InstanceActivationClient;

/**
 * Second launch / port-collision recovery: either focus the existing desktop window or open the default browser.
 */
public final class RunningInstanceNotifier {

    private RunningInstanceNotifier() {}

    public static void notifyRunningInstance(int httpPort) {
        if (SingleInstanceSupport.isDesktopMode()
                && InstanceActivationClient.tryActivate(SingleInstanceSupport.getActivationPort())) {
            return;
        }
        LocalBrowserOpener.openLocalDashboard(httpPort);
    }
}
