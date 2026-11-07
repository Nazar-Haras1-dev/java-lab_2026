package com.example.library.service;

import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import com.example.library.exception.DuplicateResourceException;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.mapper.BookMapper;
import com.example.library.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository repository;
    @Mock
    private BookMapper mapper;
    @InjectMocks
    private BookService service;

    private static Book book(Long id, String isbn) {
        Book b = new Book();
        b.setId(id);
        b.setTitle("Clean Code");
        b.setAuthor("Robert Martin");
        b.setIsbn(isbn);
        b.setPublishedYear(2008);
        return b;
    }

    private static BookDto dto(Long id, String isbn) {
        BookDto d = new BookDto();
        d.setId(id);
        d.setTitle("Clean Code");
        d.setAuthor("Robert Martin");
        d.setIsbn(isbn);
        d.setPublishedYear(2008);
        return d;
    }

    // ---------- findAll ----------

    @Test
    void findAll_booksExist_returnsMappedDtos() {
        // Arrange
        Book b1 = book(1L, "111");
        Book b2 = book(2L, "222");
        BookDto d1 = dto(1L, "111");
        BookDto d2 = dto(2L, "222");
        when(repository.findAll()).thenReturn(List.of(b1, b2));
        when(mapper.toDto(b1)).thenReturn(d1);
        when(mapper.toDto(b2)).thenReturn(d2);

        // Act
        List<BookDto> result = service.findAll();

        // Assert
        assertThat(result).containsExactly(d1, d2);
        verify(repository).findAll();
    }

    @Test
    void findAll_noBooks_returnsEmptyListWithoutMapping() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of());

        // Act
        List<BookDto> result = service.findAll();

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(mapper);
    }

    // ---------- findById ----------

    @Test
    void findById_existingId_returnsMappedDto() {
        // Arrange
        Book entity = book(1L, "111");
        BookDto expected = dto(1L, "111");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(expected);

        // Act
        BookDto result = service.findById(1L);

        // Assert
        assertThat(result).isSameAs(expected);
    }

    @Test
    void findById_missingId_throwsResourceNotFound() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Book with id 99 not found");
        verifyNoInteractions(mapper);
    }

    // ---------- count ----------

    @Test
    void count_booksExist_returnsRepositoryCount() {
        // Arrange
        when(repository.count()).thenReturn(5L);

        // Act
        long result = service.count();

        // Assert
        assertThat(result).isEqualTo(5L);
    }

    @Test
    void count_noBooks_returnsZero() {
        // Arrange
        when(repository.count()).thenReturn(0L);

        // Act
        long result = service.count();

        // Assert
        assertThat(result).isZero();
    }

    // ---------- create ----------

    @Test
    void create_newIsbn_savesMappedEntityAndReturnsDto() {
        // Arrange
        BookDto input = dto(null, "111");
        Book entity = book(null, "111");
        Book saved = book(10L, "111");
        BookDto output = dto(10L, "111");
        when(repository.existsByIsbn("111")).thenReturn(false);
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(output);

        // Act
        BookDto result = service.create(input);

        // Assert
        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIsbn()).isEqualTo("111");
        assertThat(result).isSameAs(output);
    }

    @Test
    void create_duplicateIsbn_throwsDuplicateAndDoesNotSave() {
        // Arrange
        BookDto input = dto(null, "111");
        when(repository.existsByIsbn("111")).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Book with ISBN 111 already exists");
        verify(repository, never()).save(any());
        verifyNoInteractions(mapper);
    }

    // ---------- update ----------

    @Test
    void update_existingBookUniqueIsbn_updatesEntityAndSaves() {
        // Arrange
        Book existing = book(1L, "111");
        BookDto input = dto(null, "999");
        BookDto output = dto(1L, "999");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByIsbnAndIdNot("999", 1L)).thenReturn(false);
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(output);

        // Act
        BookDto result = service.update(1L, input);

        // Assert
        verify(mapper).updateEntity(input, existing);
        verify(repository).save(existing);
        assertThat(result).isSameAs(output);
    }

    @Test
    void update_missingBook_throwsNotFoundAndDoesNotSave() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.update(99L, dto(null, "999")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Book with id 99 not found");
        verify(repository, never()).existsByIsbnAndIdNot(anyString(), anyLong());
        verify(repository, never()).save(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void update_isbnUsedByAnotherBook_throwsDuplicateAndDoesNotSave() {
        // Arrange
        Book existing = book(1L, "111");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByIsbnAndIdNot("222", 1L)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> service.update(1L, dto(null, "222")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Book with ISBN 222 already exists");
        verify(mapper, never()).updateEntity(any(), any());
        verify(repository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void delete_existingId_deletesFoundEntity() {
        // Arrange
        Book existing = book(1L, "111");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        service.delete(1L);

        // Assert
        verify(repository).delete(existing);
    }

    @Test
    void delete_missingId_throwsNotFoundAndDeletesNothing() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Book with id 99 not found");
        verify(repository, never()).delete(any(Book.class));
    }
}