package com.lczz.auth.config;

import com.lczz.auth.service.DevelopmentAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "lczz.dev-auth", name = "enabled", havingValue = "true")
public class DevelopmentAuthRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentAuthRunner.class);
    private final DevelopmentAuthService service;

    public DevelopmentAuthRunner(DevelopmentAuthService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.initializeAccounts();
        log.warn("Local development authentication is enabled; never enable it outside a developer machine");
    }
}
