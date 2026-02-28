package com.hello.platform.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {
    @Value("${APP_COMMIT_SHA:development}")
    private String commitSha;

    @GetMapping("/")
    public String sayHello() {
        return "Hello Platform";
    }

    @GetMapping("/version")
    public Map<String, String> getVersion() {
        return Map.of("commit_sha", commitSha);
    }

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "OK");
    }
}
