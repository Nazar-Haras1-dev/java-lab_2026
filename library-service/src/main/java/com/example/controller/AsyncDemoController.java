package com.example.library.controller;

import com.example.library.async.EmailSender;
import com.example.library.async.RecordingAsyncExceptionHandler;
import com.example.library.async.RecordingAsyncExceptionHandler.AsyncError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Лише для ADMIN (правило /api/admin/** у SecurityConfig). Демонстрація обробника помилок @Async void. */
@RestController
@RequestMapping("/api/admin/async-demo")
public class AsyncDemoController {

    private final EmailSender emailSender;
    private final RecordingAsyncExceptionHandler exceptionHandler;

    public AsyncDemoController(EmailSender emailSender, RecordingAsyncExceptionHandler exceptionHandler) {
        this.emailSender = emailSender;
        this.exceptionHandler = exceptionHandler;
    }

    /** Запускає лист із порожнім заголовком: у фоні кидається виняток, запит завершується 202. */
    @PostMapping("/fail-email")
    public ResponseEntity<Map<String, String>> failEmail() {
        emailSender.sendBookCreatedEmail("", "0000000000");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "started"));
    }

    @GetMapping("/errors")
    public List<AsyncError> errors() {
        return exceptionHandler.errors();
    }
}
