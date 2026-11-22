package com.example.library.aop;

import com.example.library.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    /** Годинник, яким керує тест: жодних Thread.sleep. */
    private static class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant start) {
            this.current = start;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Signature signature;

    private MutableClock clock;
    private RateLimitAspect aspect;

    @SuppressWarnings("unused")
    @RateLimited(maxCalls = 2, perSeconds = 30)
    void limitedMethod() {
    }

    private RateLimited annotation() throws NoSuchMethodException {
        return getClass().getDeclaredMethod("limitedMethod").getAnnotation(RateLimited.class);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-06T10:00:00Z"));
        aspect = new RateLimitAspect(clock);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("BookService.create(..)");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "pw"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limit_callsWithinLimit_proceedAndReturnResult() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn("ok");

        // Act
        Object first = aspect.limit(joinPoint, annotation());
        Object second = aspect.limit(joinPoint, annotation());

        // Assert
        assertThat(first).isEqualTo("ok");
        assertThat(second).isEqualTo("ok");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void limit_callOverLimit_throwsAndDoesNotProceed() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.limit(joinPoint, annotation());
        aspect.limit(joinPoint, annotation());

        // Act + Assert
        assertThatThrownBy(() -> aspect.limit(joinPoint, annotation()))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("2 calls per 30 seconds");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void limit_afterWindowExpires_allowsCallsAgain() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.limit(joinPoint, annotation());
        aspect.limit(joinPoint, annotation());
        clock.advance(Duration.ofSeconds(31));

        // Act
        Object result = aspect.limit(joinPoint, annotation());

        // Assert
        assertThat(result).isEqualTo("ok");
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    void limit_differentUsers_haveIndependentCounters() throws Throwable {
        // Arrange
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.limit(joinPoint, annotation());
        aspect.limit(joinPoint, annotation());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", "pw"));

        // Act
        Object result = aspect.limit(joinPoint, annotation());

        // Assert
        assertThat(result).isEqualTo("ok");
        verify(joinPoint, times(3)).proceed();
    }
}