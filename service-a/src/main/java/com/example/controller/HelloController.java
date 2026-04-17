package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

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

    @Value("${app.serviceBBaseUrl:http://lab-alb-591993737.ap-northeast-1.elb.amazonaws.com}")
    private String serviceBBaseUrl;

    private final RestClient restClient = RestClient.create();

    @GetMapping("/api/a/hello")
    public Map<String, Object> hello() {
        log.info("Handling /api/a/hello");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-a");
        result.put("message", "Hello from service-a");
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("name", "小明7");
        result.put("imageTag", imageTag);
        return result;
    }

    @GetMapping("/api/a/version")
    public Map<String, Object> version() {
        log.info("Handling /api/a/version");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-a");
        result.put("applicationName", applicationName);
        result.put("environment", environment);
        result.put("branch", branch);
        result.put("imageTag", imageTag);
        return result;
    }

    @GetMapping("/api/a/busy")
    public Map<String, Object> busy() {
        log.info("Handling /api/a/busy");

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

    @GetMapping("/api/a/call-b")
    public Map<String, Object> callB() {
        String traceId = MDC.get("traceId");
        log.info("Handling /api/a/call-b, traceId={}", traceId);

        Map<?, ?> serviceBResponse = restClient.get()
                .uri(serviceBBaseUrl + "/api/b/hello")
                .header("X-Correlation-Id", traceId == null ? "" : traceId)
                .retrieve()
                .body(Map.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "service-a");
        result.put("message", "service-a called service-b");
        result.put("traceId", traceId);
        result.put("serviceBResponse", serviceBResponse);

        log.info("Completed /api/a/call-b, traceId={}", traceId);
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