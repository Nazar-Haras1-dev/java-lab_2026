package com.example.library.controller;

import com.example.library.aop.AopDemoService;
import com.example.library.aop.AuditEntry;
import com.example.library.aop.AuditTrail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Лише для ADMIN (правило /api/admin/** у SecurityConfig). */
@RestController
@RequestMapping("/api/admin/aop-demo")
public class AopDemoController {

    private final AopDemoService demoService;
    private final AuditTrail auditTrail;

    public AopDemoController(AopDemoService demoService, AuditTrail auditTrail) {
        this.demoService = demoService;
        this.auditTrail = auditTrail;
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit() {
        return auditTrail.entries();
    }

    /** Зовнішній виклик: аудит спрацьовує, auditEntriesAdded = 1. */
    @GetMapping("/audited-external")
    public Map<String, Object> auditedExternal() {
        int before = auditTrail.size();
        String result = demoService.auditedAction();
        return Map.of("result", result, "auditEntriesAdded", auditTrail.size() - before);
    }

    /** Самовиклик: аудит НЕ спрацьовує, auditEntriesAdded = 0. */
    @GetMapping("/audited-self-invocation")
    public Map<String, Object> auditedSelfInvocation() {
        int before = auditTrail.size();
        String result = demoService.callsAuditedActionInternally();
        return Map.of("result", result, "auditEntriesAdded", auditTrail.size() - before);
    }

    @GetMapping("/rate-limited")
    public Map<String, String> rateLimited() {
        return Map.of("result", demoService.limitedAction());
    }

    @GetMapping("/retry")
    public Map<String, Object> retry(@RequestParam(defaultValue = "2") int failures) {
        int attempts = demoService.flakyLookup(new AtomicInteger(), failures);
        return Map.of("attempts", attempts);
    }
}