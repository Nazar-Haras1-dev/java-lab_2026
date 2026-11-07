package com.example.library.aop;

import java.time.Instant;

public record AuditEntry(Instant timestamp, String user, String action, String method, String details) {
}