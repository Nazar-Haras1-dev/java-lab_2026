package com.example.library.controller;

import com.example.library.resolver.CurrentUsernameArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileRestControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProfileRestController())
            .setCustomArgumentResolvers(new CurrentUsernameArgumentResolver())
            .build();

    @Test
    void profile_requestAsAlice_bodyContainsResolvedUsername() throws Exception {
        // Act + Assert
        mockMvc.perform(get("/api/profile").principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.message").value("Hello, alice"));
    }

    @Test
    void profile_requestAsBob_bodyContainsBobNotAlice() throws Exception {
        // Act + Assert
        mockMvc.perform(get("/api/profile").principal(() -> "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void profile_directCall_usesPassedUsernameInResponse() {
        // Act
        Map<String, String> result = new ProfileRestController().profile("carol");

        // Assert
        assertThat(result).containsEntry("username", "carol").containsEntry("message", "Hello, carol");
    }
}