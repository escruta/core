package com.escruta.core.controllers;

import com.escruta.core.dtos.CompleteRegistrationRequest;
import com.escruta.core.dtos.RequestEmailCodeRequest;
import com.escruta.core.dtos.VerifyEmailCodeRequest;
import com.escruta.core.entities.AccessToken;
import com.escruta.core.entities.User;
import com.escruta.core.services.AuthCodeService;
import com.escruta.core.services.TokenService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AuthenticationController Tests")
class AuthenticationControllerTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @MockitoBean
    private AuthCodeService authCodeService;

    @MockitoBean
    private TokenService tokenService;

    @Test
    @DisplayName("Should accept code request with valid email")
    void requestCode_shouldReturn200WhenEmailValid() throws Exception {
        var request = new RequestEmailCodeRequest("test@example.com");

        mockMvc
                .perform(post("/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authCodeService).requestCode("test@example.com");
    }

    @Test
    @DisplayName("Should return 400 when email is invalid")
    void requestCode_shouldReturn400WhenEmailInvalid() throws Exception {
        var request = new RequestEmailCodeRequest("not-an-email");

        mockMvc
                .perform(post("/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authCodeService, never()).requestCode(any());
    }

    @Test
    @DisplayName("Should return session when verifying code of existing user")
    void verifyCode_shouldReturnSessionWhenExistingUser() throws Exception {
        var request = new VerifyEmailCodeRequest("test@example.com", "123456");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setName("Test User");

        AccessToken session = new AccessToken();
        session.setToken("session-token");
        session.setUserId(user.getId());
        session.setExpiresAt(Instant.now().plusSeconds(3600));

        when(authCodeService.verifyCode(eq("test@example.com"), eq("123456")))
                .thenReturn(new AuthCodeService.VerificationOutcome(false, user, session, null));

        mockMvc
                .perform(post("/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.session.token").value("session-token"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("Should return verification token when verifying code of new user")
    void verifyCode_shouldReturnVerificationTokenWhenNewUser() throws Exception {
        var request = new VerifyEmailCodeRequest("new@example.com", "123456");

        when(authCodeService.verifyCode(eq("new@example.com"), eq("123456")))
                .thenReturn(new AuthCodeService.VerificationOutcome(true, null, null, "verification-token"));

        mockMvc
                .perform(post("/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.verificationToken").value("verification-token"));
    }

    @Test
    @DisplayName("Should complete registration and return session")
    void completeRegistration_shouldReturnSessionWhenValid() throws Exception {
        var request = new CompleteRegistrationRequest("new@example.com", "verification-token", "New User");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("new@example.com");
        user.setName("New User");

        AccessToken session = new AccessToken();
        session.setToken("new-session-token");
        session.setUserId(user.getId());
        session.setExpiresAt(Instant.now().plusSeconds(3600));

        when(authCodeService.completeRegistration(eq("new@example.com"), eq("verification-token"), eq("New User")))
                .thenReturn(new AuthCodeService.RegistrationOutcome(user, session));

        mockMvc
                .perform(post("/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("new-session-token"));
    }

    @Test
    @DisplayName("Should return 400 when name is blank on complete registration")
    void completeRegistration_shouldReturn400WhenNameBlank() throws Exception {
        var request = new CompleteRegistrationRequest("new@example.com", "verification-token", "");

        mockMvc
                .perform(post("/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should introspect token and return active true")
    void introspect_shouldReturnActiveWhenTokenValid() throws Exception {
        UUID testUserId = UUID.randomUUID();
        AccessToken accessToken = new AccessToken();
        accessToken.setUserId(testUserId);
        accessToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(tokenService.validateToken("valid-token")).thenReturn(Optional.of(accessToken));

        mockMvc
                .perform(post("/auth/introspect").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").value(testUserId.toString()));
    }

    @Test
    @DisplayName("Should introspect token and return active false when invalid")
    void introspect_shouldReturnInactiveWhenTokenInvalid() throws Exception {
        when(tokenService.validateToken("invalid-token")).thenReturn(Optional.empty());

        mockMvc
                .perform(post("/auth/introspect").param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
