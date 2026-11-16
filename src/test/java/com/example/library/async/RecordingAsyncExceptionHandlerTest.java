package com.example.library.async;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingAsyncExceptionHandlerTest {

    private final RecordingAsyncExceptionHandler handler = new RecordingAsyncExceptionHandler();

    @Test
    void handleUncaughtException_oneError_isRecordedWithMethodAndExceptionName() throws Exception {
        // Arrange
        Method method = EmailSender.class.getMethod("sendBookCreatedEmail", String.class, String.class);

        // Act
        handler.handleUncaughtException(new IllegalArgumentException("bad"), method, "t", "i");

        // Assert
        assertThat(handler.errors()).containsExactly(
                new RecordingAsyncExceptionHandler.AsyncError("EmailSender.sendBookCreatedEmail", "IllegalArgumentException", "bad"));
    }

    @Test
    void handleUncaughtException_moreThanLimit_keepsOnlyLatestEntries() throws Exception {
        // Arrange
        Method method = EmailSender.class.getMethod("sendBookCreatedEmail", String.class, String.class);

        // Act
        for (int i = 0; i < RecordingAsyncExceptionHandler.MAX_ERRORS + 5; i++) {
            handler.handleUncaughtException(new RuntimeException("e" + i), method);
        }

        // Assert
        assertThat(handler.size()).isEqualTo(RecordingAsyncExceptionHandler.MAX_ERRORS);
        assertThat(handler.errors().get(0).message()).isEqualTo("e5");
    }

    @Test
    void errors_nothingHappened_isEmpty() {
        // Act + Assert
        assertThat(handler.errors()).isEmpty();
        assertThat(handler.size()).isZero();
    }
}
