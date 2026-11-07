package com.example.library.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Signature signature;

    private final RetryAspect aspect = new RetryAspect();

    @SuppressWarnings("unused")
    @Retry(maxAttempts = 3)
    void retryThreeTimes() {
    }

    private Retry annotation() throws NoSuchMethodException {
        return getClass().getDeclaredMethod("retryThreeTimes").getAnnotation(Retry.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("BookService.findAll()");
    }

    @Test
    void retry_succeedsOnFirstAttempt_proceedsOnlyOnce() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn("data");

        // Act
        Object result = aspect.retry(joinPoint, annotation());

        // Assert
        assertThat(result).isEqualTo("data");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void retry_failsTwiceThenSucceeds_returnsResultAfterThreeAttempts() throws Throwable {
        // Arrange
        when(joinPoint.proceed())
                .thenThrow(new TransientDataAccessResourceException("fail 1"))
                .thenThrow(new TransientDataAccessResourceException("fail 2"))
                .thenReturn("data");

        // Act
        Object result = aspect.retry(joinPoint, annotation());

        // Assert
        assertThat(result).isEqualTo("data");
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    void retry_alwaysFails_throwsLastExceptionAfterMaxAttempts() throws Throwable {
        // Arrange
        when(joinPoint.proceed())
                .thenThrow(new TransientDataAccessResourceException("fail 1"))
                .thenThrow(new TransientDataAccessResourceException("fail 2"))
                .thenThrow(new TransientDataAccessResourceException("fail 3"));

        // Act + Assert
        assertThatThrownBy(() -> aspect.retry(joinPoint, annotation()))
                .isInstanceOf(TransientDataAccessResourceException.class)
                .hasMessage("fail 3");
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    void retry_nonRetryableException_rethrowsImmediatelyWithoutRetry() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        // Act + Assert
        assertThatThrownBy(() -> aspect.retry(joinPoint, annotation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        verify(joinPoint, times(1)).proceed();
    }
}