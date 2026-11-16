package com.example.library.aop;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Ім'я поточного користувача для аспектів. */
final class CurrentUserName {

    private CurrentUserName() {
    }

    static String get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}