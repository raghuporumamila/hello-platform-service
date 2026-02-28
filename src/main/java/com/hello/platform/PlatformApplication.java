package com.hello.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class PlatformApplication {
    private static final Logger logger = LoggerFactory.getLogger(PlatformApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
    /**
     * Log a structured message when the application is ready.
     * Since we've configured LogstashEncoder, this will output as JSON.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Platform service started successfully. Graceful shutdown enabled.");
    }
}
