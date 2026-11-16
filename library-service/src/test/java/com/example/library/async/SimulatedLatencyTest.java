package com.example.library.async;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedLatencyTest {

    @Test
    void pause_zeroDelay_returnsImmediately() {
        // Arrange
        SimulatedLatency latency = new SimulatedLatency(0);

        // Act
        long start = System.nanoTime();
        latency.pause("noop");

        // Assert
        assertThat(System.nanoTime() - start).isLessThan(500_000_000L);
        assertThat(latency.delayMs()).isZero();
    }

    @Test
    void pause_interruptedThread_throwsAndKeepsInterruptFlag() {
        // Arrange
        SimulatedLatency latency = new SimulatedLatency(10_000);
        Thread.currentThread().interrupt();

        // Act + Assert
        try {
            assertThatThrownBy(() -> latency.pause("email"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("email");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
