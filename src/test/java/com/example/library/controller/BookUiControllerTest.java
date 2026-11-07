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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookUiControllerTest {

    @Mock
    private BookService service;
    @InjectMocks
    private BookUiController controller;

    private final Model model = new ExtendedModelMap();

    private static BookDto dto() {
        BookDto d = new BookDto();
        d.setTitle("Clean Code");
        d.setAuthor("Robert Martin");
        d.setIsbn("111");
        return d;
    }

    @Test
    void home_anyRequest_redirectsToBooks() {
        // Act
        String view = controller.home();

        // Assert
        assertThat(view).isEqualTo("redirect:/books");
    }

    @Test
    void list_authenticatedUser_putsBooksUsernameAndOnlyAppRolesIntoModel() {
        // Arrange
        OidcUser user = mock(OidcUser.class);
        when(user.getPreferredUsername()).thenReturn("admin");
        doReturn(List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("OIDC_USER")))
                .when(user).getAuthorities();
        List<BookDto> books = List.of(dto());
        when(service.findAll()).thenReturn(books);

        // Act
        String view = controller.list(model, user);

        // Assert
        assertThat(view).isEqualTo("books/list");
        assertThat(model.getAttribute("books")).isSameAs(books);
        assertThat(model.getAttribute("username")).isEqualTo("admin");
        assertThat(model.getAttribute("roles")).isEqualTo(List.of("ROLE_ADMIN", "ROLE_USER"));
    }

    @Test
    void newForm_anyRequest_putsEmptyBookIntoModel() {
        // Act
        String view = controller.newForm(model);

        // Assert
        assertThat(view).isEqualTo("books/form");
        assertThat(model.getAttribute("book")).isInstanceOf(BookDto.class);
    }

    @Test
    void create_validBook_savesAndRedirects() {
        // Arrange
        BookDto book = dto();
        BindingResult binding = new BeanPropertyBindingResult(book, "book");

        // Act
        String view = controller.create(book, binding);

        // Assert
        verify(service).create(book);
        assertThat(view).isEqualTo("redirect:/books");
    }

    @Test
    void create_bindingErrors_returnsFormAndDoesNotSave() {
        // Arrange
        BookDto book = dto();
        BindingResult binding = new BeanPropertyBindingResult(book, "book");
        binding.rejectValue("title", "blank", "Title is required");

        // Act
        String view = controller.create(book, binding);

        // Assert
        assertThat(view).isEqualTo("books/form");
        verify(service, never()).create(any());
    }

    @Test
    void create_duplicateIsbn_rejectsIsbnFieldAndReturnsForm() {
        // Arrange
        BookDto book = dto();
        BindingResult binding = new BeanPropertyBindingResult(book, "book");
        when(service.create(book)).thenThrow(new DuplicateResourceException("ISBN exists"));

        // Act
        String view = controller.create(book, binding);

        // Assert
        assertThat(view).isEqualTo("books/form");
        assertThat(binding.getFieldError("isbn").getDefaultMessage()).isEqualTo("ISBN exists");
    }

    @Test
    void editForm_existingId_putsBookIntoModel() {
        // Arrange
        BookDto existing = dto();
        when(service.findById(3L)).thenReturn(existing);

        // Act
        String view = controller.editForm(3L, model);

        // Assert
        assertThat(view).isEqualTo("books/form");
        assertThat(model.getAttribute("book")).isSameAs(existing);
    }

    @Test
    void editForm_missingId_propagatesNotFound() {
        // Arrange
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("not found"));

        // Act + Assert
        assertThatThrownBy(() -> controller.editForm(99L, model))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_validBook_setsIdUpdatesAndRedirects() {
        // Arrange
        BookDto book = dto();
        BindingResult binding = new BeanPropertyBindingResult(book, "book");

        // Act
        String view = controller.update(3L, book, binding);

        // Assert
        assertThat(book.getId()).isEqualTo(3L);
        verify(service).update(3L, book);
        assertThat(view).isEqualTo("redirect:/books");
    }

    @Test
    void update_bindingErrors_returnsFormAndDoesNotUpdate() {
        // Arrange
        BookDto book = dto();
        BindingResult binding = new BeanPropertyBindingResult(book, "book");
        binding.rejectValue("title", "blank", "Title is required");

        // Act
        String view = controller.update(3L, book, binding);

        // Assert
        assertThat(view).isEqualTo("books/form");
        verify(service, never()).update(any(), any());
    }

    @Test
    void update_duplicateIsbn_rejectsIsbnFieldAndReturnsForm() {
        // Arrange
        BookDto book = dto();
        BindingResult binding = new BeanPropertyBindingResult(book, "book");
        when(service.update(3L, book)).thenThrow(new DuplicateResourceException("ISBN exists"));

        // Act
        String view = controller.update(3L, book, binding);

        // Assert
        assertThat(view).isEqualTo("books/form");
        assertThat(binding.getFieldError("isbn").getDefaultMessage()).isEqualTo("ISBN exists");
    }

    @Test
    void delete_existingId_callsServiceAndRedirects() {
        // Act
        String view = controller.delete(4L);

        // Assert
        verify(service).delete(4L);
        assertThat(view).isEqualTo("redirect:/books");
    }

    @Test
    void delete_serviceDeniesAccess_propagatesAccessDenied() {
        // Arrange
        doThrow(new AccessDeniedException("denied")).when(service).delete(4L);

        // Act + Assert
        assertThatThrownBy(() -> controller.delete(4L))
                .isInstanceOf(AccessDeniedException.class);
    }
}