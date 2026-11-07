package com.example.library.exception;

import com.example.library.dto.BookDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @SuppressWarnings("unused")
    private void dummyEndpoint(BookDto body) {
    }

    @Test
    void handleNotFound_resourceNotFound_returns404WithMessage() {
        // Arrange
        ResourceNotFoundException ex = new ResourceNotFoundException("Book with id 5 not found");

        // Act
        ProblemDetail result = handler.handleNotFound(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getDetail()).isEqualTo("Book with id 5 not found");
    }

    @Test
    void handleDuplicate_duplicateResource_returns409WithMessage() {
        // Arrange
        DuplicateResourceException ex = new DuplicateResourceException("Book with ISBN 1 already exists");

        // Act
        ProblemDetail result = handler.handleDuplicate(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(409);
        assertThat(result.getDetail()).isEqualTo("Book with ISBN 1 already exists");
    }

    @Test
    void handleValidation_fieldErrors_returns400WithJoinedFieldMessages() throws Exception {
        // Arrange
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new BookDto(), "book");
        binding.addError(new FieldError("book", "title", "Title is required"));
        binding.addError(new FieldError("book", "author", "Author is required"));
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("dummyEndpoint", BookDto.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        // Act
        ProblemDetail result = handler.handleValidation(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getDetail())
                .isEqualTo("title: Title is required; author: Author is required");
    }

    @Test
    void handleAccessDenied_accessDenied_returns403MentioningPreAuthorize() {
        // Arrange
        AccessDeniedException ex = new AccessDeniedException("Access Denied");

        // Act
        ProblemDetail result = handler.handleAccessDenied(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(403);
        assertThat(result.getDetail()).contains("@PreAuthorize");
    }
    @Test
    void handleRateLimit_rateLimitExceeded_returns429WithMessage() {
        // Arrange
        RateLimitExceededException ex = new RateLimitExceededException("Rate limit exceeded");

        // Act
        ProblemDetail result = handler.handleRateLimit(ex);

        // Assert
        assertThat(result.getStatus()).isEqualTo(429);
        assertThat(result.getDetail()).isEqualTo("Rate limit exceeded");
    }
}