package com.example.library.validation;

import com.example.library.dto.BookDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IsbnValidationTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<ConstraintViolation<BookDto>> validateIsbn(String isbn) {
        BookDto dto = new BookDto();
        dto.setTitle("Clean Code");
        dto.setAuthor("Robert Martin");
        dto.setIsbn(isbn);
        dto.setPublishedYear(2008);
        return VALIDATOR.validateProperty(dto, "isbn");
    }

    private static String singleMessage(Set<ConstraintViolation<BookDto>> violations) {
        assertThat(violations).hasSize(1);
        return violations.iterator().next().getMessage();
    }

    // ---------- AC2: коректні дані, порушень немає ----------

    @Test
    void isbn_validIsbn13_noViolations() {
        assertThat(validateIsbn("9780132350884")).isEmpty();
    }

    @Test
    void isbn_validIsbn13WithHyphens_noViolations() {
        assertThat(validateIsbn("978-0-13-235088-4")).isEmpty();
    }

    @Test
    void isbn_validIsbn10_noViolations() {
        assertThat(validateIsbn("0132350882")).isEmpty();
    }

    @Test
    void isbn_validIsbn10EndingWithX_noViolations() {
        assertThat(validateIsbn("080442957X")).isEmpty();
    }

    @Test
    void isbn_null_noIsbnViolationBecauseNotBlankHandlesIt() {
        // Act
        Set<ConstraintViolation<BookDto>> violations = validateIsbn(null);

        // Assert
        assertThat(violations)
                .noneMatch(v -> v.getConstraintDescriptor().getAnnotation() instanceof ValidIsbn);
    }

    // ---------- AC3: порушення з зрозумілим повідомленням ----------

    @Test
    void isbn_wrongIsbn13CheckDigit_violationExplainsCheckDigit() {
        // Act
        String message = singleMessage(validateIsbn("9780132350885"));

        // Assert
        assertThat(message).contains("ISBN-13 check digit is incorrect").contains("expected 4");
    }

    @Test
    void isbn_wrongIsbn10CheckDigit_violationExplainsCheckDigit() {
        // Act
        String message = singleMessage(validateIsbn("0132350883"));

        // Assert
        assertThat(message).contains("ISBN-10 check digit is incorrect");
    }

    @Test
    void isbn_wrongLength_violationMentionsAllowedLengths() {
        // Act
        String message = singleMessage(validateIsbn("12345"));

        // Assert
        assertThat(message).contains("10 or 13 digits").contains("contains 5");
    }

    @Test
    void isbn_lettersInsideIsbn13_violationMentionsOnlyDigits() {
        // Act
        String message = singleMessage(validateIsbn("978013235088X"));

        // Assert
        assertThat(message).contains("only digits");
    }
}