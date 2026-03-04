package com.hello.platform.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET / should return greeting text")
    void shouldReturnGreeting() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello Platform"));
    }

    @Test
    @DisplayName("GET /version should return the commit SHA")
    void shouldReturnVersionInfo() throws Exception {
        mockMvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // Verifies the JSON key exists. Since the value defaults to 'development'
                // in your @Value annotation, we check for that.
                .andExpect(jsonPath("$.commit_sha").exists());
    }

    @Test
    @DisplayName("GET /health should return status OK")
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("OK"));
    }
}