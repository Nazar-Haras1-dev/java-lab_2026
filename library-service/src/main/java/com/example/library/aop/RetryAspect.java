package com.example.library.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class RetryAspect {

    private static final Logger log = LoggerFactory.getLogger(RetryAspect.class);

    @Around("@annotation(retry)")
    public Object retry(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        int attempts = Math.max(1, retry.maxAttempts());
        Throwable lastFailure = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return joinPoint.proceed();
            } catch (Throwable ex) {
                if (!isRetryable(ex, retry.retryOn())) {
                    throw ex;
                }
                lastFailure = ex;
                log.warn("Attempt {}/{} of {} failed: {}", attempt, attempts,
                        joinPoint.getSignature().toShortString(), ex.getMessage());
            }
        }
        throw lastFailure;
    }

    private static boolean isRetryable(Throwable ex, Class<? extends Throwable>[] retryOn) {
        for (Class<? extends Throwable> type : retryOn) {
            if (type.isInstance(ex)) {
                return true;
            }
        }
        return false;
    }
}