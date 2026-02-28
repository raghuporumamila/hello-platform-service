package com.hello.platform.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller providing basic hello, version, and health check endpoints.
 */
@RestController
public final class HelloController {

    /**
     * The commit SHA of the current application build.
     */
    @Value("${APP_COMMIT_SHA:development}")
    private String commitSha;

    /**
     * Returns a simple greeting message.
     *
     * @return A string greeting.
     */
    @GetMapping("/")
    public String sayHello() {
        return "Hello Platform";
    }

    /**
     * Retrieves the current version/commit information of the application.
     *
     * @return A map containing the commit SHA.
     */
    @GetMapping("/version")
    public Map<String, String> getVersion() {
        return Map.of("commit_sha", commitSha);
    }

    /**
     * Performs a basic health check to confirm the service is running.
     *
     * @return A map containing the status "OK".
     */
    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "OK");
    }
}