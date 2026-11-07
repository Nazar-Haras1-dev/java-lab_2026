package com.example.library.controller;

import com.example.library.annotation.CurrentUsername;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileRestController {

    @GetMapping
    public Map<String, String> profile(@CurrentUsername String username) {
        return Map.of("username", username, "message", "Hello, " + username);
    }
}