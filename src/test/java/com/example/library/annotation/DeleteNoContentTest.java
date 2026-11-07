package com.example.library.annotation;

import com.example.library.controller.BookRestController;
import com.example.library.dto.BookDto;
import com.example.library.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeleteNoContentTest {

    @Mock
    private BookService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookRestController(service)).build();
    }

    @Test
    void delete_existingId_returns204WithEmptyBodyAndCallsService() throws Exception {
        // Act + Assert
        mockMvc.perform(delete("/api/books/5"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).delete(5L);
    }

    @Test
    void delete_collectionPathWithoutId_returns405() throws Exception {
        // Act + Assert
        mockMvc.perform(delete("/api/books"))
                .andExpect(status().isMethodNotAllowed());
        verify(service, never()).delete(any());
    }

    @Test
    void get_sameBookPath_isStillRoutedToGetById() throws Exception {
        // Arrange
        BookDto dto = new BookDto();
        dto.setId(5L);
        dto.setTitle("Clean Code");
        when(service.findById(5L)).thenReturn(dto);

        // Act + Assert
        mockMvc.perform(get("/api/books/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
        verify(service, never()).delete(any());
    }

    @Test
    void composedAnnotation_mergedAttributes_matchOriginalAnnotations() throws Exception {
        // Arrange
        Method method = BookRestController.class.getDeclaredMethod("delete", Long.class);

        // Act
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);

        // Assert
        assertThat(mapping.method()).containsExactly(RequestMethod.DELETE);
        assertThat(mapping.path()).containsExactly("/{id}");
        assertThat(responseStatus.code()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}