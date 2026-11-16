package com.example.library.aop;

import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AopDemoService {

    /** Анотований метод. Через проксі аудит записується. */
    @Audited(action = "DEMO_ACTION")
    public String auditedAction() {
        return "audited action executed";
    }

    /**
     * САМОВИКЛИК: this.auditedAction() іде в обхід проксі, тому аудит НЕ записується.
     * Це свідомий задум вправи, а не помилка.
     */
    public String callsAuditedActionInternally() {
        return this.auditedAction();
    }

    @RateLimited(maxCalls = 3, perSeconds = 30)
    public String limitedAction() {
        return "limited action executed";
    }

    /** Імітує тимчасову помилку: перші {@code failuresBeforeSuccess} викликів падають. */
    @Retry(maxAttempts = 3)
    public int flakyLookup(AtomicInteger callCounter, int failuresBeforeSuccess) {
        int call = callCounter.incrementAndGet();
        if (call <= failuresBeforeSuccess) {
            throw new TransientDataAccessResourceException("Simulated transient failure, call #" + call);
        }
        return call;
    }
}