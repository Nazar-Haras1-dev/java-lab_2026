package com.example.library.async;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Імітує повільну зовнішню дію (SMTP, генерація PDF, HTTP-виклик) на ~1 секунду.
 * Тривалість налаштовується: library.async.simulated-delay-ms. У тестах підміняється.
 */
@Component
public class SimulatedLatency {

    private final long delayMs;

    public SimulatedLatency(@Value("${library.async.simulated-delay-ms:1000}") long delayMs) {
        this.delayMs = delayMs;
    }

    public void pause(String operation) {
        if (delayMs <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during " + operation, e);
        }
    }

    public long delayMs() {
        return delayMs;
    }
}
