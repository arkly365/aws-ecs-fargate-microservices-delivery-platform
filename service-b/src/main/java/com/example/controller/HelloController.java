package com.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Value("${app.environment:unknown}")
    private String environment;

    @Value("${app.branch:unknown}")
    private String branch;

    @Value("${app.imageTag:unknown}")
    private String imageTag;

    @GetMapping("/api/b/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-b");
        result.put("message", "Hello from service-b");
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }

    @GetMapping("/api/b/version")
    public Map<String, Object> version() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-b");
        result.put("applicationName", applicationName);
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }
    
    @GetMapping("/api/a/busy")
    public Map<String, Object> busy() {
        long end = System.currentTimeMillis() + 5000;
        long x = 0;
        while (System.currentTimeMillis() < end) {
            x += System.nanoTime() % 100;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("service", "service-a");
        result.put("message", "busy done");
        result.put("value", x);
        return result;
    }

    @GetMapping("/")
    public ProbeResponse root() {
        return new ProbeResponse("service-b", "UP");
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nDisallow:\n";
    }

    public record ProbeResponse(String service, String status) {}
}
