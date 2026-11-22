package com.example.scheduler;

/**
 * Порт: звідки scheduler-service бере кількість книг. Не залежить від library-service (жодного імпорту його коду);
 * поточна реалізація читає спільну БД, а в майбутньому її можна замінити на HTTP-виклик.
 */
public interface BookCountProvider {
    long countBooks();
}
