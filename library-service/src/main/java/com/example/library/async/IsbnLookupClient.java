package com.example.library.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/** Повільний побічний ефект №3: виклик зовнішнього сервісу за видавцем (відповідь імітується). */
@Service
public class IsbnLookupClient {

    private final SimulatedLatency latency;

    public IsbnLookupClient(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Async(AsyncConfig.EXECUTOR_NAME)
    public CompletableFuture<String> fetchPublisher(String isbn) {
        latency.pause("isbn-lookup");
        return CompletableFuture.completedFuture("Publisher-" + isbn);
    }
}
