package com.example.library.controller;

import com.example.library.async.BookReport;
import com.example.library.async.BookReportService;
import com.example.library.exception.ResourceNotFoundException;
import com.example.library.exception.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookReportRestControllerTest {

    @Mock
    private BookReportService reportService;
    @InjectMocks
    private BookReportRestController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    void report_existingBook_returns200WithCombinedReport() throws Exception {
        // Arrange
        when(reportService.buildReport(1L)).thenReturn(new BookReport(1L, "Clean Code", 42, "Prentice Hall", false));

        // Act + Assert
        mockMvc.perform(get("/api/books/1/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.pdfBytes").value(42))
                .andExpect(jsonPath("$.publisher").value("Prentice Hall"))
                .andExpect(jsonPath("$.degraded").value(false));
    }

    @Test
    void report_unknownBook_returns404() throws Exception {
        // Arrange
        when(reportService.buildReport(9L)).thenThrow(new ResourceNotFoundException("Book with id 9 not found"));

        // Act + Assert
        mockMvc.perform(get("/api/books/9/report")).andExpect(status().isNotFound());
    }

    @Test
    void report_executorSaturated_returns503WithRetryAfterNot500() throws Exception {
        // Arrange
        when(reportService.buildReport(1L)).thenThrow(new TaskRejectedException("pool is full"));

        // Act + Assert
        mockMvc.perform(get("/api/books/1/report"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail").value("Server is busy, please try again later"));
    }
}
