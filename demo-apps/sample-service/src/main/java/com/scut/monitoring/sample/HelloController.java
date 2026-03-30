package com.scut.monitoring.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "service", "sample-service",
                "message", "hello from app-node",
                "timestamp", Instant.now().toString()
        );
    }
}
