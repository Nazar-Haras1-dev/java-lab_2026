package com.example.library.controller;

import com.example.library.async.EmailSender;
import com.example.library.async.RecordingAsyncExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AsyncDemoControllerTest {

    @Mock
    private EmailSender emailSender;
    @Mock
    private RecordingAsyncExceptionHandler exceptionHandler;

    @Test
    void failEmail_called_returns202AndStartsBlankTitleEmail() throws Exception {
        // Arrange
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AsyncDemoController(emailSender, exceptionHandler)).build();

        // Act + Assert
        mockMvc.perform(post("/api/admin/async-demo/fail-email"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("started"));
        verify(emailSender).sendBookCreatedEmail("", "0000000000");
    }

    @Test
    void errors_handlerHasRecords_returnsThem() throws Exception {
        // Arrange
        when(exceptionHandler.errors()).thenReturn(List.of(
                new RecordingAsyncExceptionHandler.AsyncError("EmailSender.sendBookCreatedEmail", "IllegalArgumentException", "bad")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AsyncDemoController(emailSender, exceptionHandler)).build();

        // Act + Assert
        mockMvc.perform(get("/api/admin/async-demo/errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exception").value("IllegalArgumentException"));
    }
}
