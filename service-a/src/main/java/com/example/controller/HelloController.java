package com.example.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/api/a/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-a");
        result.put("message", "Hello from service-a");
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }

    @GetMapping("/api/a/version")
    public Map<String, Object> version() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-a");
        result.put("applicationName", applicationName);
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }

    @GetMapping("/")
    public ProbeResponse root() {
        return new ProbeResponse("service-a", "UP");
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nDisallow:\n";
    }

    public record ProbeResponse(String service, String status) {}
}
