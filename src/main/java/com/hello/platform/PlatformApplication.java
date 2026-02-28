package com.hello.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Main entry point for the Platform Application.
 */
@SpringBootApplication
public class PlatformApplication {
    private static final Logger logger = LoggerFactory.getLogger(PlatformApplication.class);

    /**
     * Private constructor to prevent instantiation of this utility-like class.
     */
    private PlatformApplication() {
        // Prevent instantiation
    }

    /**
     * Starts the Spring Boot application.
     * * @param args command line arguments
     */
    public static void main(final String[] args) {
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