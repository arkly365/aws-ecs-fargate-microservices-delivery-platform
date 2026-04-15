package com.example.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.repository.UserRepository;

@RestController
@RequestMapping("/api/a")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers() {
        return userRepository.findAll().stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail()))
                .toList();
    }

    @GetMapping("/ping-db")
    public Map<String, Object> pingDb() {
        long count = userRepository.count();
        return Map.of(
                "status", "ok",
                "service", "service-a",
                "userCount", count
        );
    }
}
