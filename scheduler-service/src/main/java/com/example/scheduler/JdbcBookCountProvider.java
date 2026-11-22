package com.example.scheduler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Лише читання: SELECT count(*) FROM books. Без дублювання сутності Book. */
@Component
public class JdbcBookCountProvider implements BookCountProvider {

    private final JdbcTemplate jdbcTemplate;

    public JdbcBookCountProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long countBooks() {
        Long count = jdbcTemplate.queryForObject("select count(*) from books", Long.class);
        return count == null ? 0L : count;
    }
}
