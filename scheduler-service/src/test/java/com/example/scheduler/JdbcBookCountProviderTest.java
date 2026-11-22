package com.example.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcBookCountProviderTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @InjectMocks
    private JdbcBookCountProvider provider;

    @Test
    void countBooks_tableHasRows_returnsCountFromReadOnlySelect() {
        // Arrange
        when(jdbcTemplate.queryForObject("select count(*) from books", Long.class)).thenReturn(7L);

        // Act
        long result = provider.countBooks();

        // Assert
        assertThat(result).isEqualTo(7L);
        verify(jdbcTemplate).queryForObject("select count(*) from books", Long.class);
    }

    @Test
    void countBooks_driverReturnsNull_returnsZero() {
        // Arrange
        when(jdbcTemplate.queryForObject("select count(*) from books", Long.class)).thenReturn(null);

        // Act + Assert
        assertThat(provider.countBooks()).isZero();
    }
}
