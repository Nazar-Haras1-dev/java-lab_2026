package com.example.library.async;

import com.example.library.dto.BookDto;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookReportServiceTest {

    @Mock
    private BookService bookService;
    @Mock
    private PdfGenerator pdfGenerator;
    @Mock
    private IsbnLookupClient isbnLookupClient;

    private BookReportService service;
    private BookDto book;

    @BeforeEach
    void setUp() {
        service = new BookReportService(bookService, pdfGenerator, isbnLookupClient, 200);
        book = new BookDto();
        book.setId(7L);
        book.setTitle("Clean Code");
        book.setAuthor("Robert Martin");
        book.setIsbn("9780132350884");
    }

    @Test
    void buildReport_bothCallsSucceed_combinesResultsNotDegraded() {
        // Arrange
        when(bookService.findById(7L)).thenReturn(book);
        when(pdfGenerator.generateBookCard(book)).thenReturn(CompletableFuture.completedFuture(new byte[42]));
        when(isbnLookupClient.fetchPublisher("9780132350884")).thenReturn(CompletableFuture.completedFuture("Prentice Hall"));

        // Act
        BookReport report = service.buildReport(7L);

        // Assert
        assertThat(report).isEqualTo(new BookReport(7L, "Clean Code", 42, "Prentice Hall", false));
    }

    @Test
    void buildReport_pdfFails_returnsDegradedReportWithoutThrowing() {
        // Arrange
        when(bookService.findById(7L)).thenReturn(book);
        when(pdfGenerator.generateBookCard(book)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));
        when(isbnLookupClient.fetchPublisher(any())).thenReturn(CompletableFuture.completedFuture("Prentice Hall"));

        // Act
        BookReport report = service.buildReport(7L);

        // Assert
        assertThat(report.degraded()).isTrue();
        assertThat(report.pdfBytes()).isZero();
        assertThat(report.publisher()).isEqualTo("Prentice Hall");
    }

    @Test
    void buildReport_publisherFails_returnsDegradedReportWithUnknownPublisher() {
        // Arrange
        when(bookService.findById(7L)).thenReturn(book);
        when(pdfGenerator.generateBookCard(book)).thenReturn(CompletableFuture.completedFuture(new byte[5]));
        when(isbnLookupClient.fetchPublisher(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("503 from partner")));

        // Act
        BookReport report = service.buildReport(7L);

        // Assert
        assertThat(report.degraded()).isTrue();
        assertThat(report.pdfBytes()).isEqualTo(5);
        assertThat(report.publisher()).isEqualTo("unknown");
    }

    @Test
    void buildReport_bothFail_returnsFullyDegradedReport() {
        // Arrange
        when(bookService.findById(7L)).thenReturn(book);
        when(pdfGenerator.generateBookCard(book)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("a")));
        when(isbnLookupClient.fetchPublisher(any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("b")));

        // Act
        BookReport report = service.buildReport(7L);

        // Assert
        assertThat(report).isEqualTo(new BookReport(7L, "Clean Code", 0, "unknown", true));
    }

    @Test
    void buildReport_futureNeverCompletes_stopsWaitingAfterTimeoutAndCancelsBoth() {
        // Arrange
        CompletableFuture<byte[]> pdf = new CompletableFuture<>();
        CompletableFuture<String> publisher = new CompletableFuture<>();
        when(bookService.findById(7L)).thenReturn(book);
        when(pdfGenerator.generateBookCard(book)).thenReturn(pdf);
        when(isbnLookupClient.fetchPublisher(any())).thenReturn(publisher);

        // Act
        BookReport report = service.buildReport(7L);

        // Assert
        assertThat(report.degraded()).isTrue();
        assertThat(report.publisher()).isEqualTo("unknown");
        assertThat(pdf.isCancelled()).isTrue();
        assertThat(publisher.isCancelled()).isTrue();
    }

    @Test
    void buildReport_secondSubmitRejected_cancelsFirstAndPropagatesTaskRejected() {
        // Arrange
        CompletableFuture<byte[]> pdf = new CompletableFuture<>();
        when(bookService.findById(7L)).thenReturn(book);
        when(pdfGenerator.generateBookCard(book)).thenReturn(pdf);
        when(isbnLookupClient.fetchPublisher(any())).thenThrow(new TaskRejectedException("pool is full"));

        // Act + Assert
        assertThatThrownBy(() -> service.buildReport(7L)).isInstanceOf(TaskRejectedException.class);
        assertThat(pdf.isCancelled()).isTrue();
    }

    @Test
    void buildReport_unknownBook_throwsNotFoundAndStartsNoAsyncWork() {
        // Arrange
        when(bookService.findById(99L)).thenThrow(new ResourceNotFoundException("Book with id 99 not found"));

        // Act + Assert
        assertThatThrownBy(() -> service.buildReport(99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(pdfGenerator, isbnLookupClient);
    }

    @Test
    void buildReport_interruptedCaller_returnsDegradedAndKeepsInterruptFlag() {
        // Arrange
        when(bookService.findById(7L)).thenReturn(book);
        when(pdfGenerator.generateBookCard(book)).thenReturn(new CompletableFuture<>());
        when(isbnLookupClient.fetchPublisher(any())).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        // Act
        BookReport report;
        boolean flagKept;
        try {
            report = service.buildReport(7L);
        } finally {
            flagKept = Thread.interrupted();   // читає і скидає прапорець, щоб не зачепити інші тести
        }

        // Assert
        assertThat(report.degraded()).isTrue();
        assertThat(flagKept).isTrue();
        verify(isbnLookupClient).fetchPublisher("9780132350884");
        verify(bookService, never()).count();
    }
}
