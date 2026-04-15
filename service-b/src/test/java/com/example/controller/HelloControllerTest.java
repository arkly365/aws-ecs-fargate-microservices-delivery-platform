package com.example.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.environment=test",
        "app.branch=develop",
        "app.imageTag=build-123"
})
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hello_should_return_service_a_info() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("service-b"))
                .andExpect(jsonPath("$.message").value("Hello from service-b"))
                .andExpect(jsonPath("$.environment").value("test"))
                .andExpect(jsonPath("$.branch").value("develop"))
                .andExpect(jsonPath("$.imageTag").value("build-123"));
    }

    @Test
    void version_should_return_version_info() throws Exception {
        mockMvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("service-b"))
                .andExpect(jsonPath("$.environment").value("test"))
                .andExpect(jsonPath("$.branch").value("develop"))
                .andExpect(jsonPath("$.imageTag").value("build-123"));
    }

    @Test
    void root_should_return_probe_status() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("service-b"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void robots_should_return_text() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string("User-agent: *\nDisallow:\n"));
    }

    @Test
    void sitemap_should_return_xml() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<loc>/</loc>")))
                .andExpect(content().string(containsString("<loc>/hello</loc>")))
                .andExpect(content().string(containsString("<loc>/actuator/health</loc>")))
                .andExpect(content().string(containsString("<loc>/version</loc>")));
    }
}