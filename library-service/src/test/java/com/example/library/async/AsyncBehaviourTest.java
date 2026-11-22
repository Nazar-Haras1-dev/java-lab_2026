package com.example.library.async;

import com.example.library.async.AsyncTestSupport.ControlledLatency;
import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;

import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Сценарії Lab 5 на реальних @Async-проксі у легкому Spring-контексті (без Spring Boot, без БД).
 * Жодного Thread.sleep: синхронізація через CountDownLatch / CyclicBarrier / Awaitility.
 */
class AsyncBehaviourTest {

    private static BookDto book() {
        BookDto b = new BookDto();
        b.setId(1L);
        b.setTitle("Clean Code");
        b.setAuthor("Robert Martin");
        b.setIsbn("9780132350884");
        return b;
    }

    /** Спільна конфігурація: реальні @Async-біни + мок BookService + підміна затримки (SimulatedLatency). */
    @Configuration
    @Import(AsyncConfig.class)
    abstract static class BaseBeans {
        @Bean
        BookService bookService() {
            BookService service = Mockito.mock(BookService.class);
            Mockito.when(service.findById(1L)).thenReturn(book());
            return service;
        }

        @Bean
        PdfGenerator pdfGenerator(SimulatedLatency latency) {
            return new PdfGenerator(latency);
        }

        @Bean
        IsbnLookupClient isbnLookupClient(SimulatedLatency latency) {
            return new IsbnLookupClient(latency);
        }

        @Bean
        EmailSender emailSender(SimulatedLatency latency) {
            return new EmailSender(latency);
        }

        @Bean
        BookReportService bookReportService(BookService bookService, PdfGenerator pdf, IsbnLookupClient isbn,
                                            @org.springframework.beans.factory.annotation.Value("${library.async.report-timeout-ms:5000}") long timeout) {
            return new BookReportService(bookService, pdf, isbn, timeout);
        }
    }

    // ---------- паралельність: бар'єр на 2 учасники, послідовне виконання його б не пройшло ----------

    static class BarrierLatency extends SimulatedLatency {
        private final CyclicBarrier barrier = new CyclicBarrier(2);

        BarrierLatency() {
            super(0);
        }

        @Override
        public void pause(String operation) {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("operations did not run concurrently: " + operation, e);
            }
        }
    }

    @Configuration
    static class BarrierBeans extends BaseBeans {
        @Bean
        SimulatedLatency latency() {
            return new BarrierLatency();
        }
    }

    @Test
    void buildReport_twoIndependentCalls_runConcurrentlyAndResultsAreCombined() {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), BarrierBeans.class)) {
            BookReportService service = ctx.getBean(BookReportService.class);

            // Act: якщо виклики йдуть послідовно, бар'єр на двох не відкриється і звіт буде degraded
            BookReport report = service.buildReport(1L);

            // Assert
            assertThat(report.degraded()).isFalse();
            assertThat(report.publisher()).isEqualTo("Publisher-9780132350884");
            assertThat(report.pdfBytes()).isPositive();
        }
    }

    // ---------- збій void-методу фіксується обробником ----------

    @Configuration
    static class FailingEmailBeans extends BaseBeans {
        @Bean
        SimulatedLatency latency() {
            return new ControlledLatency(null);
        }
    }

    @Test
    void sendBookCreatedEmail_voidMethodThrows_callerContinuesAndHandlerRecordsError() {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), FailingEmailBeans.class)) {
            EmailSender sender = ctx.getBean(EmailSender.class);
            RecordingAsyncExceptionHandler handler = ctx.getBean(RecordingAsyncExceptionHandler.class);

            // Act: виклик не кидає виняток у викликаючому коді
            sender.sendBookCreatedEmail("", "123");

            // Assert
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(handler.size()).isEqualTo(1));
            assertThat(handler.errors().get(0).method()).isEqualTo("EmailSender.sendBookCreatedEmail");
            assertThat(handler.errors().get(0).exception()).isEqualTo("IllegalArgumentException");
        }
    }

    @Test
    void sendBookCreatedEmail_validArguments_handlerRecordsNothing() {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), FailingEmailBeans.class)) {
            ControlledLatency latency = ctx.getBean(ControlledLatency.class);
            RecordingAsyncExceptionHandler handler = ctx.getBean(RecordingAsyncExceptionHandler.class);

            // Act
            ctx.getBean(EmailSender.class).sendBookCreatedEmail("Clean Code", "123");

            // Assert
            await().atMost(5, TimeUnit.SECONDS).until(() -> !latency.threads.isEmpty());
            assertThat(handler.size()).isZero();
        }
    }

    // ---------- збій Future: caller не падає ----------

    @Test
    void buildReport_asyncCallsThrow_returnsDegradedReportInsteadOfFailing() {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), FailingEmailBeans.class)) {
            ctx.getBean(ControlledLatency.class).failWith = new IllegalStateException("partner is down");

            // Act
            BookReport report = ctx.getBean(BookReportService.class).buildReport(1L);

            // Assert
            assertThat(report.degraded()).isTrue();
            assertThat(report.publisher()).isEqualTo("unknown");
            assertThat(report.pdfBytes()).isZero();
        }
    }

    // ---------- обмежене очікування ----------

    @Configuration
    static class SlowBeans extends BaseBeans {
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Bean
        SimulatedLatency latency() {
            return new ControlledLatency(RELEASE);
        }
    }

    @Test
    void buildReport_asyncCallsTooSlow_givesUpAfterTimeout() {
        // Arrange
        try (AnnotationConfigApplicationContext ctx =
                     AsyncTestSupport.context(Map.of("library.async.report-timeout-ms", "100"), SlowBeans.class)) {
            // Act
            BookReport report = ctx.getBean(BookReportService.class).buildReport(1L);
            SlowBeans.RELEASE.countDown();

            // Assert
            assertThat(report.degraded()).isTrue();
            assertThat(report.publisher()).isEqualTo("unknown");
        }
    }

    // ---------- переповнення виконавця ----------

    @Configuration
    static class TinyPoolBeans extends BaseBeans {
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Bean
        SimulatedLatency latency() {
            return new ControlledLatency(RELEASE);
        }
    }

    @Test
    void buildReport_executorSaturated_throwsTaskRejectedExplicitlyInsteadOfHanging() {
        // Arrange: 1 потік, черги немає => другий submit гарантовано відхиляється
        Map<String, Object> tinyPool = Map.of(
                "library.async.core-pool-size", "1",
                "library.async.max-pool-size", "1",
                "library.async.queue-capacity", "0");
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(tinyPool, TinyPoolBeans.class)) {
            BookReportService service = ctx.getBean(BookReportService.class);

            // Act + Assert
            assertThatThrownBy(() -> service.buildReport(1L))
                    .isInstanceOf(TaskRejectedException.class);
            TinyPoolBeans.RELEASE.countDown();
        }
    }

    @Test
    void sendBookCreatedEmail_executorSaturated_secondCallIsRejectedWithTaskRejected() throws Exception {
        // Arrange
        Map<String, Object> tinyPool = Map.of(
                "library.async.core-pool-size", "1",
                "library.async.max-pool-size", "1",
                "library.async.queue-capacity", "0");
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(tinyPool, TinyPoolBeans2.class)) {
            EmailSender sender = ctx.getBean(EmailSender.class);
            ControlledLatency latency = ctx.getBean(ControlledLatency.class);
            sender.sendBookCreatedEmail("First", "1");
            latency.entered.await(5, TimeUnit.SECONDS);

            // Act + Assert
            assertThatThrownBy(() -> sender.sendBookCreatedEmail("Second", "2"))
                    .isInstanceOf(TaskRejectedException.class);
            TinyPoolBeans2.RELEASE.countDown();
        }
    }

    @Configuration
    static class TinyPoolBeans2 extends BaseBeans {
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Bean
        SimulatedLatency latency() {
            return new ControlledLatency(RELEASE);
        }
    }

    // ---------- не-Async код не міняється ----------

    @Test
    void generateBookCard_resultFuture_completesWithContentOfTheBook() throws Exception {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), FailingEmailBeans.class)) {
            // Act
            CompletableFuture<byte[]> future = ctx.getBean(PdfGenerator.class).generateBookCard(book());

            // Assert
            assertThat(new String(future.get(5, TimeUnit.SECONDS))).contains("Clean Code", "9780132350884");
        }
    }
}
