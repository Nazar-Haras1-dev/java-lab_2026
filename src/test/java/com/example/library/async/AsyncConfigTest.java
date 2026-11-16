package com.example.library.async;

import com.example.library.async.AsyncTestSupport.ControlledLatency;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Configuration
    @Import(AsyncConfig.class)
    static class Beans {
        static final CountDownLatch RELEASE = new CountDownLatch(1);

        @Bean
        ControlledLatency latency() {
            return new ControlledLatency(RELEASE);
        }

        @Bean
        PdfGenerator pdfGenerator(SimulatedLatency latency) {
            return new PdfGenerator(latency);
        }
    }

    @Test
    void libraryTaskExecutor_defaultProperties_hasExplicitPoolSizesQueueAndPrefix() {
        // Arrange
        AsyncConfig config = new AsyncConfig(4, 8, 10);

        // Act
        ThreadPoolTaskExecutor executor = config.libraryTaskExecutor();
        executor.initialize();
        try {
            // Assert
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            assertThat(executor.getCorePoolSize()).isEqualTo(4);
            assertThat(executor.getMaxPoolSize()).isEqualTo(8);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(10);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("library-async-");
            assertThat(pool.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void getAsyncExecutor_called_returnsSameLibraryExecutorNotSpringDefault() {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), Beans.class)) {
            // Act
            Object defaultExecutor = ctx.getBean(AsyncConfig.class).getAsyncExecutor();

            // Assert
            assertThat(defaultExecutor).isSameAs(ctx.getBean(AsyncConfig.EXECUTOR_NAME));
            assertThat(defaultExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        }
    }

    @Test
    void getAsyncUncaughtExceptionHandler_called_returnsRecordingHandlerBean() {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), Beans.class)) {
            // Act
            Object handler = ctx.getBean(AsyncConfig.class).getAsyncUncaughtExceptionHandler();

            // Assert
            assertThat(handler).isSameAs(ctx.getBean(RecordingAsyncExceptionHandler.class));
        }
    }

    @Test
    void asyncMethod_called_returnsBeforeCompletionAndRunsInPrefixedThread() throws Exception {
        // Arrange
        try (AnnotationConfigApplicationContext ctx = AsyncTestSupport.context(Map.of(), Beans.class)) {
            PdfGenerator generator = ctx.getBean(PdfGenerator.class);
            ControlledLatency latency = ctx.getBean(ControlledLatency.class);

            // Act: метод заблокований на латчі, але виклик одразу повертає керування
            CompletableFuture<byte[]> future = generator.generateBookCard(new com.example.library.dto.BookDto());
            boolean started = latency.entered.await(5, TimeUnit.SECONDS);
            boolean doneRightAfterCall = future.isDone();
            Beans.RELEASE.countDown();
            byte[] result = future.get(5, TimeUnit.SECONDS);

            // Assert
            assertThat(started).isTrue();
            assertThat(doneRightAfterCall).isFalse();
            assertThat(result).isNotEmpty();
            assertThat(latency.threads).hasSize(1);
            assertThat(latency.threads.get(0))
                    .startsWith(AsyncConfig.THREAD_NAME_PREFIX)
                    .doesNotContain("SimpleAsyncTaskExecutor")
                    .isNotEqualTo(Thread.currentThread().getName());
        }
    }
}
