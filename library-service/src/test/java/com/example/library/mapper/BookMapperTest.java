package com.example.library.mapper;

import com.example.library.dto.BookDto;
import com.example.library.entity.Book;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookMapperTest {

    private final BookMapper mapper = new BookMapperImpl();

    private static Book book() {
        Book b = new Book();
        b.setId(1L);
        b.setTitle("Clean Code");
        b.setAuthor("Robert Martin");
        b.setIsbn("111");
        b.setPublishedYear(2008);
        return b;
    }

    private static BookDto dto() {
        BookDto d = new BookDto();
        d.setId(50L);
        d.setTitle("New title");
        d.setAuthor("New author");
        d.setIsbn("999");
        d.setPublishedYear(2020);
        return d;
    }

    @Test
    void toDto_book_mapsAllFields() {
        // Act
        BookDto result = mapper.toDto(book());

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Clean Code");
        assertThat(result.getAuthor()).isEqualTo("Robert Martin");
        assertThat(result.getIsbn()).isEqualTo("111");
        assertThat(result.getPublishedYear()).isEqualTo(2008);
    }

    @Test
    void toEntity_dto_mapsFieldsAndIgnoresId() {
        // Act
        Book result = mapper.toEntity(dto());

        // Assert
        assertThat(result.getId()).isNull();
        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getAuthor()).isEqualTo("New author");
        assertThat(result.getIsbn()).isEqualTo("999");
        assertThat(result.getPublishedYear()).isEqualTo(2020);
    }

    @Test
    void updateEntity_dto_overwritesFieldsAndKeepsExistingId() {
        // Arrange
        Book target = book();

        // Act
        mapper.updateEntity(dto(), target);

        // Assert
        assertThat(target.getId()).isEqualTo(1L);
        assertThat(target.getTitle()).isEqualTo("New title");
        assertThat(target.getIsbn()).isEqualTo("999");
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toEntity_null_returnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void updateEntity_nullDto_leavesBookUnchanged() {
        // Arrange
        Book target = book();

        // Act
        mapper.updateEntity(null, target);

        // Assert
        assertThat(target.getTitle()).isEqualTo("Clean Code");
        assertThat(target.getIsbn()).isEqualTo("111");
    }
}