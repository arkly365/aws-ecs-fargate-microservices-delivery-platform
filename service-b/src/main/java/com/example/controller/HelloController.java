package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

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
        String traceId = MDC.get("traceId");
        log.info("Handling /api/b/hello srvice-b , traceId={}", traceId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-b");
        result.put("message", "Hello from service-b");
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("name", "小華");
        result.put("imageTag", imageTag);
        result.put(" sys time", new Date());
        result.put("traceId", traceId);
        return result;
    }

    @GetMapping("/api/b/version")
    public Map<String, Object> version() {
        log.info("Handling /api/b/version");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-b");
        result.put("applicationName", applicationName);
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }

    @GetMapping("/api/b/busy")
    public Map<String, Object> busy() {
        log.info("Handling /api/b/busy");

        long end = System.currentTimeMillis() + 5000;
        long x = 0;
        while (System.currentTimeMillis() < end) {
            x += System.nanoTime() % 100;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("service", "service-b");
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