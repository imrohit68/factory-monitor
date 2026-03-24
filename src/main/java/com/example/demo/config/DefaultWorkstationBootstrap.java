package com.example.demo.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultWorkstationBootstrap implements ApplicationRunner {

    private final DefaultWorkstationSeedService seedService;

    public DefaultWorkstationBootstrap(DefaultWorkstationSeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedService.ensureDefaultWorkstations();
    }
}
