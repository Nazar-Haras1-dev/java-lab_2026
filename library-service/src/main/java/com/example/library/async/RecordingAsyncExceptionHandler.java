package com.example.library.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Фіксує (логує й запам'ятовує) помилки @Async void-методів, щоб вони не зникали непомітно. */
public class RecordingAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RecordingAsyncExceptionHandler.class);
    static final int MAX_ERRORS = 100;

    public record AsyncError(String method, String exception, String message) { }

    private final ConcurrentLinkedDeque<AsyncError> errors = new ConcurrentLinkedDeque<>();

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("Uncaught exception in @Async method {}: {}", method.getName(), ex.toString(), ex);
        errors.addLast(new AsyncError(method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                ex.getClass().getSimpleName(), ex.getMessage()));
        while (errors.size() > MAX_ERRORS) {
            errors.pollFirst();
        }
    }

    public List<AsyncError> errors() {
        return List.copyOf(errors);
    }

    public int size() {
        return errors.size();
    }
}
