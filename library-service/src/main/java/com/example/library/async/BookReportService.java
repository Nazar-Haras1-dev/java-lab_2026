package com.example.library.async;

import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Викликаючий код: запускає ДВА незалежні повільні виклики одразу (не чекаючи), потім об'єднує
 * результати через thenCombine. Очікування обмежене таймаутом, збій одного виклику не валить запит.
 * TaskRejectedException (пул переповнений) свідомо НЕ перехоплюється тут - її перетворює на 503 RestExceptionHandler.
 */
@Service
public class BookReportService {

    private static final Logger log = LoggerFactory.getLogger(BookReportService.class);
    static final String UNKNOWN_PUBLISHER = "unknown";

    private final BookService bookService;
    private final PdfGenerator pdfGenerator;
    private final IsbnLookupClient isbnLookupClient;
    private final long timeoutMs;

    public BookReportService(BookService bookService,
                             PdfGenerator pdfGenerator,
                             IsbnLookupClient isbnLookupClient,
                             @Value("${library.async.report-timeout-ms:5000}") long timeoutMs) {
        this.bookService = bookService;
        this.pdfGenerator = pdfGenerator;
        this.isbnLookupClient = isbnLookupClient;
        this.timeoutMs = timeoutMs;
    }

    public BookReport buildReport(Long bookId) {
        BookDto book = bookService.findById(bookId);

        // Обидва виклики стартують одразу, ніхто з них не чекає на інший
        CompletableFuture<byte[]> pdf = pdfGenerator.generateBookCard(book);
        CompletableFuture<String> publisher;
        try {
            publisher = isbnLookupClient.fetchPublisher(book.getIsbn());
        } catch (RuntimeException e) {          // зокрема TaskRejectedException на другому submit
            pdf.cancel(true);
            throw e;
        }

        CompletableFuture<byte[]> pdfSafe = pdf.exceptionally(ex -> {
            log.warn("PDF generation failed for book {}: {}", bookId, ex.toString());
            return null;
        });
        CompletableFuture<String> publisherSafe = publisher.exceptionally(ex -> {
            log.warn("Publisher lookup failed for book {}: {}", bookId, ex.toString());
            return null;
        });

        CompletableFuture<BookReport> combined = pdfSafe.thenCombine(publisherSafe, (bytes, pub) ->
                new BookReport(book.getId(), book.getTitle(),
                        bytes == null ? 0 : bytes.length,
                        pub == null ? UNKNOWN_PUBLISHER : pub,
                        bytes == null || pub == null));

        try {
            return combined.get(timeoutMs, TimeUnit.MILLISECONDS);   // очікування ЗАВЖДИ обмежене
        } catch (TimeoutException e) {
            log.warn("Report for book {} timed out after {} ms", bookId, timeoutMs);
            pdf.cancel(true);
            publisher.cancel(true);
            return fallback(book);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback(book);
        } catch (ExecutionException e) {
            log.warn("Report for book {} failed: {}", bookId, e.getCause().toString());
            return fallback(book);
        }
    }

    private static BookReport fallback(BookDto book) {
        return new BookReport(book.getId(), book.getTitle(), 0, UNKNOWN_PUBLISHER, true);
    }
}
