package com.escruta.core.controllers;

import com.escruta.core.dtos.session.SessionResponseDTO;
import com.escruta.core.services.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SessionController Tests")
class SessionControllerTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @MockitoBean
    private SessionService sessionService;

    @Test
    @WithMockUser
    @DisplayName("Should return current user's sessions when authenticated")
    void getSessions_shouldReturnSessions() throws Exception {
        when(sessionService.getSessions()).thenReturn(List.of(new SessionResponseDTO(
                "session-id",
                "Mozilla/5.0",
                "127.0.0.1",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                true
        )));

        mockMvc
                .perform(get("/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("session-id"))
                .andExpect(jsonPath("$[0].userAgent").value("Mozilla/5.0"))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("Should revoke a session when it exists")
    void revokeSession_shouldReturnNoContentWhenFound() throws Exception {
        when(sessionService.revokeSession("session-id")).thenReturn(true);

        mockMvc.perform(delete("/sessions/session-id")).andExpect(status().isNoContent());

        verify(sessionService).revokeSession("session-id");
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 404 when session does not belong to user")
    void revokeSession_shouldReturnNotFoundWhenMissing() throws Exception {
        when(sessionService.revokeSession("unknown")).thenReturn(false);

        mockMvc.perform(delete("/sessions/unknown")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("Should revoke other sessions")
    void revokeOtherSessions_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/sessions")).andExpect(status().isNoContent());

        verify(sessionService).revokeOtherSessions();
    }
}
