package com.example.library.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null і порожнє значення перевіряє @NotBlank, щоб не дублювати повідомлення
        if (value == null || value.isBlank()) {
            return true;
        }
        String isbn = value.replace("-", "").replace(" ", "");
        String error = validate(isbn);
        if (error == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        return false;
    }

    /** Повертає текст помилки або null, якщо ISBN коректний. */
    private static String validate(String isbn) {
        if (isbn.length() != 10 && isbn.length() != 13) {
            return "ISBN must contain 10 or 13 digits, but contains " + isbn.length();
        }
        return isbn.length() == 10 ? checkIsbn10(isbn) : checkIsbn13(isbn);
    }

    private static String checkIsbn13(String isbn) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            char c = isbn.charAt(i);
            if (c < '0' || c > '9') {
                return "ISBN-13 may contain only digits (hyphens and spaces are allowed as separators)";
            }
            sum += (c - '0') * (i % 2 == 0 ? 1 : 3);
        }
        char last = isbn.charAt(12);
        if (last < '0' || last > '9') {
            return "ISBN-13 may contain only digits (hyphens and spaces are allowed as separators)";
        }
        int expected = (10 - sum % 10) % 10;
        return expected == last - '0' ? null : "ISBN-13 check digit is incorrect (expected " + expected + ")";
    }

    private static String checkIsbn10(String isbn) {
        String charsError = "ISBN-10 may contain only digits, with an optional X as the last character";
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = isbn.charAt(i);
            if (c < '0' || c > '9') {
                return charsError;
            }
            sum += (c - '0') * (10 - i);
        }
        char last = Character.toUpperCase(isbn.charAt(9));
        if (last == 'X') {
            sum += 10;
        } else if (last >= '0' && last <= '9') {
            sum += last - '0';
        } else {
            return charsError;
        }
        return sum % 11 == 0 ? null : "ISBN-10 check digit is incorrect";
    }
}