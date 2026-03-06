package com.escruta.core.controllers;

import com.escruta.core.dtos.LoginUserDto;
import com.escruta.core.dtos.RegisterUserDto;
import com.escruta.core.entities.AccessToken;
import com.escruta.core.entities.User;
import com.escruta.core.services.TokenService;
import com.escruta.core.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AuthenticationController Tests")
class AuthenticationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TokenService tokenService;

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void login_shouldReturnTokenWhenValidCredentials() throws Exception {
        LoginUserDto loginDto = new LoginUserDto();
        loginDto.setEmail("test@example.com");
        loginDto.setPassword("Password123");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test@example.com");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        AccessToken accessToken = new AccessToken();
        accessToken.setToken("test-token-123");
        accessToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(tokenService.createToken("test@example.com")).thenReturn(accessToken);

        mockMvc
                .perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token-123"));
    }

    @Test
    @DisplayName("Should return 400 when email is blank")
    void login_shouldReturn400WhenEmailBlank() throws Exception {
        LoginUserDto loginDto = new LoginUserDto();
        loginDto.setEmail("");
        loginDto.setPassword("Password123");

        mockMvc
                .perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when password is too short")
    void login_shouldReturn400WhenPasswordTooShort() throws Exception {
        LoginUserDto loginDto = new LoginUserDto();
        loginDto.setEmail("test@example.com");
        loginDto.setPassword("short");

        mockMvc
                .perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when email is invalid")
    void login_shouldReturn400WhenEmailInvalid() throws Exception {
        LoginUserDto loginDto = new LoginUserDto();
        loginDto.setEmail("invalid-email");
        loginDto.setPassword("Password123");

        mockMvc
                .perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should register successfully with valid data")
    void register_shouldReturnTokenWhenValidData() throws Exception {
        RegisterUserDto registerDto = new RegisterUserDto();
        registerDto.setEmail("newuser@example.com");
        registerDto.setPassword("Password123");
        registerDto.setName("New User");

        User registeredUser = new User();
        registeredUser.setEmail("newuser@example.com");
        registeredUser.setName("New User");

        when(userService.register(any())).thenReturn(registeredUser);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("newuser@example.com");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        AccessToken accessToken = new AccessToken();
        accessToken.setToken("new-token-123");
        accessToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(tokenService.createToken("newuser@example.com")).thenReturn(accessToken);

        mockMvc
                .perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("new-token-123"));
    }

    @Test
    @DisplayName("Should return 400 when registration email is invalid")
    void register_shouldReturn400WhenEmailInvalid() throws Exception {
        RegisterUserDto registerDto = new RegisterUserDto();
        registerDto.setEmail("not-an-email");
        registerDto.setPassword("Password123");
        registerDto.setName("New User");

        mockMvc
                .perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when registration password is too weak")
    void register_shouldReturn400WhenPasswordWeak() throws Exception {
        RegisterUserDto registerDto = new RegisterUserDto();
        registerDto.setEmail("newuser@example.com");
        registerDto.setPassword("weak");
        registerDto.setName("New User");

        mockMvc
                .perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when full name is blank")
    void register_shouldReturn400WhenNameBlank() throws Exception {
        RegisterUserDto registerDto = new RegisterUserDto();
        registerDto.setEmail("newuser@example.com");
        registerDto.setPassword("Password123");
        registerDto.setName("");

        mockMvc
                .perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should introspect token and return active true")
    void introspect_shouldReturnActiveWhenTokenValid() throws Exception {
        AccessToken accessToken = new AccessToken();
        accessToken.setEmail("test@example.com");
        accessToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));

        when(tokenService.validateToken("valid-token")).thenReturn(Optional.of(accessToken));

        mockMvc
                .perform(post("/introspect").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").value("test@example.com"));
    }

    @Test
    @DisplayName("Should introspect token and return active false when invalid")
    void introspect_shouldReturnInactiveWhenTokenInvalid() throws Exception {
        when(tokenService.validateToken("invalid-token")).thenReturn(Optional.empty());

        mockMvc
                .perform(post("/introspect").param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
