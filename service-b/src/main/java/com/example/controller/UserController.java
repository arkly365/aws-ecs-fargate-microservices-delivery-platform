package com.example.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.entity.User;
import com.example.repository.UserRepository;


@RestController
@RequestMapping("/api/b")
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
    
    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestParam String name,
                                          @RequestParam String email) {
        User user = new User(name, email);
        User saved = userRepository.save(user);

        return Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "email", saved.getEmail(),
                "status", "created"
        );
    }

    @GetMapping("/ping-db")
    public Map<String, Object> pingDb() {
        long count = userRepository.count();
        return Map.of(
                "status", "ok",
                "service", "service-b",
                "userCount", count
        );
    }
}
