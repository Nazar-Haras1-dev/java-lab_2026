package com.example.library.controller;

import com.example.library.dto.BookDto;
import com.example.library.exception.DuplicateResourceException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.service.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookRestControllerTest {

    @Mock
    private BookService service;
    @InjectMocks
    private BookRestController controller;

    private static BookDto dto(Long id) {
        BookDto d = new BookDto();
        d.setId(id);
        d.setTitle("Clean Code");
        d.setAuthor("Robert Martin");
        d.setIsbn("111");
        return d;
    }

    @Test
    void getAll_booksExist_returnsListFromService() {
        // Arrange
        List<BookDto> books = List.of(dto(1L), dto(2L));
        when(service.findAll()).thenReturn(books);

        // Act
        List<BookDto> result = controller.getAll();

        // Assert
        assertThat(result).isSameAs(books);
    }

    @Test
    void getAll_noBooks_returnsEmptyList() {
        // Arrange
        when(service.findAll()).thenReturn(List.of());

        // Act
        List<BookDto> result = controller.getAll();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getById_existingId_returnsDtoFromService() {
        // Arrange
        BookDto expected = dto(1L);
        when(service.findById(1L)).thenReturn(expected);

        // Act
        BookDto result = controller.getById(1L);

        // Assert
        assertThat(result).isSameAs(expected);
    }

    @Test
    void getById_serviceThrowsNotFound_propagatesException() {
        // Arrange
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("Book with id 99 not found"));

        // Act + Assert
        assertThatThrownBy(() -> controller.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_validDto_returns201WithLocationAndBody() {
        // Arrange
        BookDto input = dto(null);
        BookDto saved = dto(7L);
        when(service.create(input)).thenReturn(saved);

        // Act
        ResponseEntity<BookDto> response = controller.create(input);

        // Assert
        verify(service).create(input);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/books/7");
        assertThat(response.getBody()).isSameAs(saved);
    }

    @Test
    void create_serviceThrowsDuplicate_propagatesException() {
        // Arrange
        BookDto input = dto(null);
        when(service.create(input)).thenThrow(new DuplicateResourceException("dup"));

        // Act + Assert
        assertThatThrownBy(() -> controller.create(input))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_validDto_returnsUpdatedDtoFromService() {
        // Arrange
        BookDto input = dto(null);
        BookDto updated = dto(3L);
        when(service.update(3L, input)).thenReturn(updated);

        // Act
        BookDto result = controller.update(3L, input);

        // Assert
        verify(service).update(3L, input);
        assertThat(result).isSameAs(updated);
    }

    @Test
    void update_serviceThrowsNotFound_propagatesException() {
        // Arrange
        BookDto input = dto(null);
        when(service.update(99L, input)).thenThrow(new ResourceNotFoundException("not found"));

        // Act + Assert
        assertThatThrownBy(() -> controller.update(99L, input))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_existingId_callsServiceAndReturns204() {
        // Act
        ResponseEntity<Void> response = controller.delete(5L);

        // Assert
        verify(service).delete(5L);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void delete_serviceDeniesAccess_propagatesAccessDenied() {
        // Arrange
        doThrow(new AccessDeniedException("denied")).when(service).delete(5L);

        // Act + Assert
        assertThatThrownBy(() -> controller.delete(5L))
                .isInstanceOf(AccessDeniedException.class);
    }
}