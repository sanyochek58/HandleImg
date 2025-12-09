package com.example.HanldeImg.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Configurable;

@Configurable
public class LoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(LoggingConfig.class);

    @PostConstruct
    public void init() {
        log.info("Logging is initialized");
    }
}
