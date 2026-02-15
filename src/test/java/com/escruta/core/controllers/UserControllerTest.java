package com.escruta.core.controllers;

import com.escruta.core.dtos.ChangePasswordDto;
import com.escruta.core.entities.User;
import com.escruta.core.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("UserController Tests")
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser
    @DisplayName("Should return current user when authenticated")
    void getMe_shouldReturnUserWhenAuthenticated() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFullName("Test User");

        when(userService.getCurrentFullUser()).thenReturn(user);

        mockMvc
                .perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    @WithMockUser
    @DisplayName("Should change name successfully when authenticated")
    void changeName_shouldChangeNameWhenAuthenticated() throws Exception {
        mockMvc.perform(post("/users/change-name").param("newFullName", "New Name")).andExpect(status().isOk());

        verify(userService).changeName("New Name");
    }

    @Test
    @WithMockUser
    @DisplayName("Should change password successfully when authenticated")
    void changePassword_shouldChangePasswordWhenAuthenticated() throws Exception {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("OldPassword123");
        dto.setNewPassword("NewPassword123");

        mockMvc
                .perform(post("/users/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(userService).changePassword(any(ChangePasswordDto.class));
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 400 when change password request is invalid")
    void changePassword_shouldReturn400WhenInvalid() throws Exception {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("short");
        dto.setNewPassword("weak");

        mockMvc
                .perform(post("/users/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("Should delete account successfully when authenticated")
    void deleteAccount_shouldDeleteWhenAuthenticated() throws Exception {
        mockMvc.perform(delete("/users/me")).andExpect(status().isNoContent());

        verify(userService).deleteAccount();
    }
}
