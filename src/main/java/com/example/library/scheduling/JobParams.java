package com.example.library.scheduling;

import org.quartz.JobDataMap;

/**
 * Читання параметрів із JobDataMap з явною поведінкою:
 * ключа немає (або null) -> документоване значення за замовчуванням;
 * значення неправильного типу/формату -> IllegalArgumentException (завдання не виконується).
 */
final class JobParams {

    private JobParams() { }

    static String string(JobDataMap map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new IllegalArgumentException("Job parameter '" + key + "' must be a non-blank string, got: " + value);
    }

    static int positiveInt(JobDataMap map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Job parameter '" + key + "' must be an integer, got: " + value, e);
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException("Job parameter '" + key + "' must be positive, got: " + parsed);
        }
        return parsed;
    }
}
