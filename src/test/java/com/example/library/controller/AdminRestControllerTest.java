package com.example.library.controller;

import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRestControllerTest {

    @Mock
    private BookService service;
    @InjectMocks
    private AdminRestController controller;

    @Test
    void stats_booksExist_returnsTotalBooksFromService() {
        // Arrange
        when(service.count()).thenReturn(3L);

        // Act
        Map<String, Long> result = controller.stats();

        // Assert
        verify(service).count();
        assertThat(result).containsEntry("totalBooks", 3L);
    }

    @Test
    void stats_noBooks_returnsZero() {
        // Arrange
        when(service.count()).thenReturn(0L);

        // Act
        Map<String, Long> result = controller.stats();

        // Assert
        assertThat(result).containsEntry("totalBooks", 0L);
    }
}