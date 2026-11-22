package com.example.library.controller;

import com.example.library.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminRestController {

    private final BookService service;

    public AdminRestController(BookService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of("totalBooks", service.count());
    }
}