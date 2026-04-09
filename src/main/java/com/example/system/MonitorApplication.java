package com.example.system;

import com.example.system.config.AppProperties;
import com.example.system.config.ModbusProperties;
import com.example.system.config.RunningInstanceNotifier;
import com.example.system.config.SingleInstanceSupport;
import com.example.system.desktop.FactoryMonitorDesktopApplication;
import javafx.application.Application;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ModbusProperties.class, AppProperties.class})
public class MonitorApplication {

    private static final Logger log = LoggerFactory.getLogger(MonitorApplication.class);

    public static void main(String[] args) {
        SingleInstanceSupport.prepareBeforeSpring(args);
        if (SingleInstanceSupport.isDesktopMode()) {
            Platform.setImplicitExit(false);
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
            Application.launch(FactoryMonitorDesktopApplication.class, args);
            return;
        }
        runSpringApplication(args);
    }

    /** Used by the desktop shell (JavaFX) to run Spring Boot on a worker thread. */
    public static ConfigurableApplicationContext runSpringApplication(String[] args) {
        SpringApplication app = new SpringApplication(MonitorApplication.class);
        app.addListeners(
                (ApplicationListener<ApplicationFailedEvent>)
                        event -> {
                            if (!SingleInstanceSupport.isSingleInstanceEnabled()) {
                                return;
                            }
                            if (!SingleInstanceSupport.isLikelyPortBindFailure(event.getException())) {
                                return;
                            }
                            int port = SingleInstanceSupport.getConfiguredPort();
                            log.info(
                                    "HTTP port {} is already in use; delegating to the running instance.",
                                    port);
                            RunningInstanceNotifier.notifyRunningInstance(port);
                            Runtime.getRuntime().halt(0);
                        });
        return app.run(args);
    }
}
