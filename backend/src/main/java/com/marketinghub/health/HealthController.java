package com.marketinghub.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
            "ok",
            "backend",
            System.getProperty("java.version")
        );
    }

    public record HealthResponse(String status, String service, String jvm) {}
}
