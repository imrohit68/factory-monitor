package com.example.system;

import com.example.system.config.AppProperties;
import com.example.system.config.ModbusProperties;
import com.example.system.config.SingleInstanceEnvironmentListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ModbusProperties.class, AppProperties.class})
public class MonitorApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MonitorApplication.class);
        app.addListeners(new SingleInstanceEnvironmentListener());
        app.run(args);
    }
}
