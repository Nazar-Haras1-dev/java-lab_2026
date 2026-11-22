package com.example.library.aop;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Component
public class AuditTrail {

    private static final int MAX_ENTRIES = 1000;

    private final Deque<AuditEntry> entries = new ArrayDeque<>();

    public synchronized void add(AuditEntry entry) {
        entries.addLast(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    public synchronized List<AuditEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int size() {
        return entries.size();
    }
}