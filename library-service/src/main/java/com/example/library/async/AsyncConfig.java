package com.example.library.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Явно налаштований пул потоків для {@code @Async}.
 * Розміри беруться з application.yml (library.async.*), значення за замовчуванням: 4 / 8 / 10.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String EXECUTOR_NAME = "libraryTaskExecutor";
    public static final String THREAD_NAME_PREFIX = "library-async-";

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;

    public AsyncConfig(@Value("${library.async.core-pool-size:4}") int corePoolSize,
                       @Value("${library.async.max-pool-size:8}") int maxPoolSize,
                       @Value("${library.async.queue-capacity:10}") int queueCapacity) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
    }

    @Bean(name = EXECUTOR_NAME)
    public ThreadPoolTaskExecutor libraryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        // Коли пул і черга заповнені, submit кидає RejectedExecutionException,
        // який Spring перетворює на TaskRejectedException (її обробляє RestExceptionHandler -> 503).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public RecordingAsyncExceptionHandler asyncExceptionHandler() {
        return new RecordingAsyncExceptionHandler();
    }

    /** Виконавець за замовчуванням для будь-якого @Async без імені - теж наш, а не SimpleAsyncTaskExecutor. */
    @Override
    public Executor getAsyncExecutor() {
        return libraryTaskExecutor();
    }

    /** Обробник для @Async-методів з типом повернення void. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return asyncExceptionHandler();
    }
}
