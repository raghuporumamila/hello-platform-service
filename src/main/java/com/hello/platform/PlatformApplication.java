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
public final class PlatformApplication {
    /**
     * Logger for the application.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(PlatformApplication.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private PlatformApplication() {
        // Prevent instantiation
    }

    /**
     * Starts the Spring Boot application.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }

    /**
     * Log a structured message when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        LOGGER.info("Platform service started successfully. "
                + "Graceful shutdown enabled.");
    }
}
