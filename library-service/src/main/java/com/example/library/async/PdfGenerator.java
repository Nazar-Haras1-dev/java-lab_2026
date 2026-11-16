package com.example.library.async;

import com.example.library.dto.BookDto;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** Повільний побічний ефект №2: генерація PDF-картки книги (вміст спрощено до тексту). */
@Service
public class PdfGenerator {

    private final SimulatedLatency latency;

    public PdfGenerator(SimulatedLatency latency) {
        this.latency = latency;
    }

    @Async(AsyncConfig.EXECUTOR_NAME)
    public CompletableFuture<byte[]> generateBookCard(BookDto book) {
        latency.pause("generate-pdf");
        String content = "%PDF-SIMULATED% " + book.getTitle() + " / " + book.getAuthor() + " / " + book.getIsbn();
        return CompletableFuture.completedFuture(content.getBytes(StandardCharsets.UTF_8));
    }
}
