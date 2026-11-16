package com.example.library.aop;

import org.springframework.dao.TransientDataAccessException;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Повторює виклик методу, якщо він кинув один із зазначених (тимчасових) винятків. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {

    int maxAttempts() default 3;

    Class<? extends Throwable>[] retryOn() default {TransientDataAccessException.class};
}