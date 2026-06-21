package com.scut.monitoring.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class HelloController {
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HelloController(ObjectMapper objectMapper) {
        this.httpClient = new OkHttpClient();
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "service", "sample-service",
                "message", "hello from app-node",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/api/demo-chain")
    public Map<String, Object> demoChain(@RequestParam(defaultValue = "demo-user") String user) throws Exception {
        Request request = new Request.Builder()
                .url("http://middleware-node:8082/api/middleware/profile?user="
                        + URLEncoder.encode(user, StandardCharsets.UTF_8))
                .build();
        Map<String, Object> downstream;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("middleware-service returned " + response.code());
            }
            String responseBody = response.body() == null ? "{}" : response.body().string();
            downstream = objectMapper.readValue(
                    responseBody,
                    new TypeReference<>() {
                    }
            );
        }

        return Map.of(
                "service", "sample-service",
                "entrypoint", "/api/demo-chain",
                "user", user,
                "downstream", downstream == null ? Map.of() : downstream,
                "timestamp", Instant.now().toString()
        );
    }
}
